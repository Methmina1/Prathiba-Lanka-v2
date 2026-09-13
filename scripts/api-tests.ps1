<#
.SYNOPSIS
    End-to-end API test for the PrathibaLanka Spring Boot backend.

.DESCRIPTION
    Exercises every REST endpoint (auth, packages, gallery, journal, contact/queries, bookings,
    reviews) including negative and authorization cases, and asserts the exact HTTP status codes
    the API is supposed to return.

    The script is self-contained: it registers its own customer accounts, creates its own data
    (unique per run) and deletes what it created at the end. Bookings and contact queries have no
    delete endpoint, so those rows stay in the database.

.PARAMETER BaseUrl
    Base URL of a RUNNING backend instance. Default: http://localhost:18080

.PARAMETER AdminEmail / AdminPassword
    Credentials of an admin account. Defaults match the bootstrap admin configured in
    application.properties (app.bootstrap-admin.*).

.EXAMPLE
    # start the app first, then:
    powershell -ExecutionPolicy Bypass -File scripts/api-tests.ps1 -BaseUrl http://localhost:18080

.NOTES
    Exit code 0 = every check passed, 1 = at least one check failed.
#>
[CmdletBinding()]
param(
    [string]$BaseUrl       = 'http://localhost:18080',
    [string]$AdminEmail    = 'admin@test.com',
    [string]$AdminPassword = 'Admin@12345'
)

$ErrorActionPreference = 'Stop'
# Windows PowerShell 5.1 needs this assembly loaded explicitly; PowerShell 7 already ships it.
try { Add-Type -AssemblyName System.Net.Http -ErrorAction Stop } catch { }

$script:Pass     = 0
$script:Fail     = 0
$script:Failures = New-Object System.Collections.ArrayList
# The concurrency checks fire several requests at once, so the client must not queue them.
$httpHandler = New-Object System.Net.Http.HttpClientHandler
$httpHandler.MaxConnectionsPerServer = 100
$script:Http     = New-Object System.Net.Http.HttpClient($httpHandler)
$script:Http.Timeout = [TimeSpan]::FromSeconds(60)
$script:RunId    = Get-Date -Format 'yyyyMMddHHmmss'

# ---------------------------------------------------------------- helpers

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        $Body,
        [string]$Token
    )
    $methodObj = New-Object System.Net.Http.HttpMethod($Method.ToUpperInvariant())
    $req = New-Object System.Net.Http.HttpRequestMessage($methodObj, "$BaseUrl$Path")
    if ($null -ne $Body) {
        $json = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 6 }
        $req.Content = New-Object System.Net.Http.StringContent($json, [System.Text.Encoding]::UTF8, 'application/json')
    }
    if ($Token) {
        $req.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $Token)
    }
    $resp = $script:Http.SendAsync($req).Result
    $text = $resp.Content.ReadAsStringAsync().Result
    $parsed = $null
    if ($text) { try { $parsed = $text | ConvertFrom-Json } catch { $parsed = $null } }
    [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = $text; Json = $parsed }
}

function Test-Api {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Path,
        $Body,
        [string]$Token,
        [int[]]$Expect = @(200),
        [scriptblock]$Check,
        [string]$CheckDesc = '',
        # Only assigned calls ask for the response object; otherwise the raw response would be
        # dumped into the console output of every check.
        [switch]$Capture
    )
    $r = Invoke-Api -Method $Method -Path $Path -Body $Body -Token $Token
    $ok     = $Expect -contains $r.Status
    $detail = "expected $($Expect -join '/'), got $($r.Status)"

    if ($ok -and $Check) {
        $checkOk = $false
        try { $checkOk = [bool](& $Check $r) } catch { $detail = "body check threw: $($_.Exception.Message)" }
        if ($checkOk) { $detail = "OK - $CheckDesc" }
        elseif ($detail -notlike 'body check threw*') { $ok = $false; $detail = "body check failed - $CheckDesc" }
        else { $ok = $false }
    }

    if ($ok) {
        $script:Pass++
        Write-Host ("  [PASS] {0,-56} HTTP {1}" -f $Name, $r.Status) -ForegroundColor Green
    } else {
        $script:Fail++
        [void]$script:Failures.Add("$Name -> $detail")
        Write-Host ("  [FAIL] {0,-56} {1}" -f $Name, $detail) -ForegroundColor Red
        if ($r.Body) {
            $snippet = $r.Body -replace '\s+', ' '
            Write-Host ("         body: " + $snippet.Substring(0, [Math]::Min(280, $snippet.Length))) -ForegroundColor DarkYellow
        }
    }
    if ($Capture) { return $r }
}

function Section([string]$Title) {
    Write-Host ''
    Write-Host "=== $Title " -ForegroundColor Cyan -NoNewline
    Write-Host ('=' * [Math]::Max(4, 64 - $Title.Length)) -ForegroundColor Cyan
}

# Fires Count requests before waiting for any of them, so they really are in flight together.
function Invoke-Concurrent {
    param(
        [string]$Method,
        [string]$Path,
        $Body,
        [string]$Token,
        [int]$Count = 2
    )
    $tasks = New-Object System.Collections.ArrayList
    for ($i = 0; $i -lt $Count; $i++) {
        $methodObj = New-Object System.Net.Http.HttpMethod($Method.ToUpperInvariant())
        $req = New-Object System.Net.Http.HttpRequestMessage($methodObj, "$BaseUrl$Path")
        if ($null -ne $Body) {
            $json = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 6 }
            $req.Content = New-Object System.Net.Http.StringContent($json, [System.Text.Encoding]::UTF8, 'application/json')
        }
        if ($Token) {
            $req.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $Token)
        }
        [void]$tasks.Add($script:Http.SendAsync($req))
    }
    $results = foreach ($t in $tasks) {
        $resp = $t.Result
        [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = $resp.Content.ReadAsStringAsync().Result }
    }
    return @($results)
}

function Wait-Until {
    param([scriptblock]$Condition, [int]$TimeoutSeconds = 15, [int]$IntervalMs = 500)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        if (& $Condition) { return $true }
        Start-Sleep -Milliseconds $IntervalMs
    } while ((Get-Date) -lt $deadline)
    return $false
}

# For assertions that need time: async mail, for example, is sent after the response was written.
function Test-Condition {
    param(
        [string]$Name,
        [scriptblock]$Check,
        [string]$CheckDesc = '',
        [int]$TimeoutSeconds = 15
    )
    if (Wait-Until -Condition $Check -TimeoutSeconds $TimeoutSeconds) {
        $script:Pass++
        Write-Host ("  [PASS] {0,-56} {1}" -f $Name, "OK - $CheckDesc") -ForegroundColor Green
    } else {
        $script:Fail++
        [void]$script:Failures.Add("$Name -> not satisfied within ${TimeoutSeconds}s - $CheckDesc")
        Write-Host ("  [FAIL] {0,-56} {1}" -f $Name, "not satisfied within ${TimeoutSeconds}s") -ForegroundColor Red
    }
}

# ---------------------------------------------------------------- 0. setup

Write-Host ''
Write-Host "PrathibaLanka API test run $($RunId) against $BaseUrl" -ForegroundColor White
Write-Host "Admin: $AdminEmail"

Section '0. Connectivity & admin bootstrap'

$adminLogin = Test-Api -Name 'admin can log in (bootstrap admin is usable)' -Method Post -Path '/api/auth/login' `
    -Body @{ email = $AdminEmail; password = $AdminPassword } -Expect 201, 200 `
    -Check { param($r) $r.Json.role -eq 'ROLE_ADMIN' } -CheckDesc 'role is ROLE_ADMIN' -Capture

$adminToken = $adminLogin.Json.token
if (-not $adminToken) {
    Write-Host ''
    Write-Host 'Cannot continue without an admin token. Is the app running and is the bootstrap admin seeded?' -ForegroundColor Red
    exit 1
}

# ---------------------------------------------------------------- 1. auth

Section '1. Authentication (/api/auth)'

$custAEmail = "cust-a-$($RunId)@example.com"
$custBEmail = "cust-b-$($RunId)@example.com"

$regA = Test-Api -Name 'register customer A' -Method Post -Path '/api/auth/register' `
    -Body @{ fullName = 'Customer A'; email = $custAEmail; phone = '0771234567'; password = 'Passw0rd!' } -Expect 201 `
    -Check { param($r) $r.Json.token -and $r.Json.role -eq 'ROLE_CUSTOMER' } -CheckDesc 'token + ROLE_CUSTOMER returned' -Capture
$custAToken = $regA.Json.token
$custAId    = $regA.Json.userId

$regB = Test-Api -Name 'register customer B' -Method Post -Path '/api/auth/register' `
    -Body @{ fullName = 'Customer B'; email = $custBEmail; password = 'Passw0rd!' } -Expect 201 -Capture
$custBToken = $regB.Json.token
$custBId    = $regB.Json.userId

Test-Api -Name 'register duplicate email is rejected' -Method Post -Path '/api/auth/register' `
    -Body @{ fullName = 'Copy Cat'; email = $custAEmail; password = 'Passw0rd!' } -Expect 400

Test-Api -Name 'register with invalid payload -> 400 (was 500)' -Method Post -Path '/api/auth/register' `
    -Body @{ fullName = ''; email = 'not-an-email'; password = '123' } -Expect 400 `
    -Check { param($r) $r.Json.details.Count -ge 2 } -CheckDesc 'per-field validation details returned'

Test-Api -Name 'login with correct credentials' -Method Post -Path '/api/auth/login' `
    -Body @{ email = $custAEmail; password = 'Passw0rd!' } -Expect 200 `
    -Check { param($r) $r.Json.userId -eq $custAId } -CheckDesc 'same customerId as registration'

Test-Api -Name 'login with wrong password -> 401 (was 500)' -Method Post -Path '/api/auth/login' `
    -Body @{ email = $custAEmail; password = 'WrongPassword1' } -Expect 401 `
    -Check { param($r) $r.Json.message -notmatch 'Bad credentials|Exception' } -CheckDesc 'no internal details leaked'

Test-Api -Name 'login with unknown email -> 401' -Method Post -Path '/api/auth/login' `
    -Body @{ email = "nobody-$($RunId)@example.com"; password = 'Passw0rd!' } -Expect 401

Test-Api -Name 'login with invalid payload -> 400' -Method Post -Path '/api/auth/login' `
    -Body @{ email = 'bad'; password = '' } -Expect 400

# email case-insensitivity: register with mixed case, log in with lower case
$mixedEmail = "MiXeD-$($RunId)@Example.COM"
Test-Api -Name 'register with mixed-case email' -Method Post -Path '/api/auth/register' `
    -Body @{ fullName = 'Mixed Case'; email = $mixedEmail; password = 'Passw0rd!' } -Expect 201 | Out-Null
Test-Api -Name 'login is case-insensitive for email' -Method Post -Path '/api/auth/login' `
    -Body @{ email = $mixedEmail.ToLower(); password = 'Passw0rd!' } -Expect 200 `
    -Check { param($r) $r.Json.email -eq $mixedEmail.ToLower() } -CheckDesc 'stored in canonical lower case'

Test-Api -Name 'admin can log in with different email casing' -Method Post -Path '/api/auth/login' `
    -Body @{ email = $AdminEmail.ToUpper(); password = $AdminPassword } -Expect 200

# ---------------------------------------------------------------- 2. authorization

Section '2. Authorization / error dispatch'

Test-Api -Name 'admin endpoint without token -> 401' -Method Get -Path '/api/admin/packages' -Expect 401
Test-Api -Name 'admin endpoint with customer token -> 403 (was masked 401)' -Method Get -Path '/api/admin/packages' `
    -Token $custAToken -Expect 403
Test-Api -Name 'garbage token -> 401' -Method Get -Path '/api/admin/packages' -Token 'not-a-jwt' -Expect 401
Test-Api -Name 'unknown path with valid token -> 404 (was 500)' -Method Get -Path '/api/does-not-exist' `
    -Token $custAToken -Expect 404 `
    -Check { param($r) $r.Json.message -eq 'Endpoint not found.' } -CheckDesc 'clean 404 body'
Test-Api -Name 'unknown path without token -> 401' -Method Get -Path '/api/does-not-exist' -Expect 401
Test-Api -Name 'unsupported method -> 405' -Method Patch -Path '/api/packages/1' -Token $adminToken -Expect 405
Test-Api -Name 'malformed JSON body -> 400 (was 500)' -Method Post -Path '/api/auth/login' `
    -Body '{ this is not json' -Expect 400

# ---------------------------------------------------------------- 3. packages

Section '3. Travel packages'

Test-Api -Name 'GET /api/packages (public)' -Method Get -Path '/api/packages' -Expect 200 `
    -Check { param($r) $r.Json.Count -ge 1 } -CheckDesc 'at least one active package'

Test-Api -Name 'GET /api/packages/{id} (public)' -Method Get -Path '/api/packages/1' -Expect 200 `
    -Check { param($r) $r.Json.packageId -eq 1 } -CheckDesc 'returns the requested package'

Test-Api -Name 'GET /api/packages/{unknown} -> 404' -Method Get -Path '/api/packages/999999' -Expect 404 `
    -Check { param($r) $r.Json.error -eq 'Not Found' } -CheckDesc 'structured 404 body'
Test-Api -Name 'GET /api/packages/{non-numeric} -> 400 (was 500)' -Method Get -Path '/api/packages/abc' -Expect 400
Test-Api -Name 'GET /api/packages/search (public)' -Method Get -Path '/api/packages/search?destination=Bali' -Expect 200 `
    -Check { param($r) $r.Json.Count -ge 1 } -CheckDesc 'finds Bali package'
Test-Api -Name 'GET /api/packages/search without param -> 400 (was 500)' -Method Get -Path '/api/packages/search' -Expect 400
Test-Api -Name 'GET /api/packages/search with blank destination -> 400' -Method Get -Path '/api/packages/search?destination=%20' -Expect 400

Test-Api -Name 'POST /api/admin/packages without token -> 401' -Method Post -Path '/api/admin/packages' `
    -Body @{ title = 'Sneaky'; destination = 'Nowhere'; durationDays = 1; price = 10 } -Expect 401

$newPkg = Test-Api -Name 'POST /api/admin/packages (admin) -> 201' -Method Post -Path '/api/admin/packages' `
    -Token $adminToken -Expect 201 `
    -Body @{ title = "Test Package $($RunId)"; description = 'created by api-tests'; destination = 'Testland'; `
             durationDays = 3; price = 199.99; maxCapacity = 10; itinerary = 'Day 1: test'; status = 'ACTIVE' } `
    -Check { param($r) $r.Json.status -eq 'ACTIVE' } -CheckDesc 'created as ACTIVE' -Capture
$pkgId = $newPkg.Json.packageId

Test-Api -Name 'POST /api/admin/packages invalid payload -> 400' -Method Post -Path '/api/admin/packages' `
    -Token $adminToken -Expect 400 -Body @{ title = ''; destination = ''; durationDays = 0; price = -5 }

$updPkg = Test-Api -Name 'PUT /api/admin/packages/{id} (admin)' -Method Put -Path "/api/admin/packages/$pkgId" `
    -Token $adminToken -Expect 200 `
    -Body @{ title = "Updated Package $($RunId)"; description = 'updated'; destination = 'Testland'; `
             durationDays = 4; price = 249.50; maxCapacity = 12; itinerary = 'Day 1: updated'; status = 'ACTIVE' } `
    -Check { param($r) $r.Json.title -like 'Updated Package*' -and $r.Json.durationDays -eq 4 } -CheckDesc 'fields persisted' -Capture

Test-Api -Name 'PUT /api/admin/packages/{unknown} -> 404' -Method Put -Path '/api/admin/packages/999999' `
    -Token $adminToken -Expect 404 -Body @{ title = 'X'; destination = 'Y'; durationDays = 1; price = 1 }

Test-Api -Name 'GET /api/admin/packages (admin sees all)' -Method Get -Path '/api/admin/packages' `
    -Token $adminToken -Expect 200 -Check { param($r) $r.Json.Count -ge 2 } -CheckDesc 'includes inactive packages'

# a small package to test capacity, and one to deactivate
$smallPkg = Test-Api -Name 'POST /api/admin/packages (capacity 1)' -Method Post -Path '/api/admin/packages' `
    -Token $adminToken -Expect 201 `
    -Body @{ title = "Tiny Package $($RunId)"; destination = 'Tinyland'; durationDays = 1; price = 50; maxCapacity = 1 } -Capture
$smallPkgId = $smallPkg.Json.packageId

$deadPkg = Test-Api -Name 'POST /api/admin/packages (to deactivate)' -Method Post -Path '/api/admin/packages' `
    -Token $adminToken -Expect 201 `
    -Body @{ title = "Doomed Package $($RunId)"; destination = 'Doom'; durationDays = 2; price = 80; maxCapacity = 5 } -Capture
$deadPkgId = $deadPkg.Json.packageId

Test-Api -Name 'PATCH /api/admin/packages/{id}/deactivate (admin)' -Method Patch `
    -Path "/api/admin/packages/$deadPkgId/deactivate" -Token $adminToken -Expect 200 `
    -Check { param($r) $r.Json.status -eq 'INACTIVE' } -CheckDesc 'status INACTIVE'

Test-Api -Name 'deactivated package is hidden from public list' -Method Get -Path '/api/packages' -Expect 200 `
    -Check { param($r) -not ($r.Json.packageId -contains $deadPkgId) } -CheckDesc 'inactive package not listed'

# ---------------------------------------------------------------- 4. gallery

Section '4. Gallery'

Test-Api -Name 'POST /api/admin/gallery without token -> 401' -Method Post -Path '/api/admin/gallery' `
    -Body @{ imageUrl = 'https://example.com/x.jpg' } -Expect 401

$img = Test-Api -Name 'POST /api/admin/gallery (admin) -> 201' -Method Post -Path '/api/admin/gallery' `
    -Token $adminToken -Expect 201 `
    -Body @{ imageUrl = 'https://example.com/original.jpg'; caption = 'Original caption'; packageId = $pkgId } `
    -Check { param($r) $r.Json.uploadedByName } -CheckDesc 'uploader recorded' -Capture
$imgId = $img.Json.imageId

Test-Api -Name 'POST /api/admin/gallery invalid payload -> 400' -Method Post -Path '/api/admin/gallery' `
    -Token $adminToken -Expect 400 -Body @{ imageUrl = '' }

Test-Api -Name 'GET /api/gallery (public)' -Method Get -Path '/api/gallery' -Expect 200 `
    -Check { param($r) $r.Json.imageId -contains $imgId } -CheckDesc 'created image is listed'
Test-Api -Name 'GET /api/gallery/{id} (public)' -Method Get -Path "/api/gallery/$imgId" -Expect 200 `
    -Check { param($r) $r.Json.packageId -eq $pkgId } -CheckDesc 'package association returned'
Test-Api -Name 'GET /api/gallery/{unknown} -> 404' -Method Get -Path '/api/gallery/999999' -Expect 404
Test-Api -Name 'GET /api/gallery/package/{id} (public)' -Method Get -Path "/api/gallery/package/$pkgId" -Expect 200 `
    -Check { param($r) $r.Json.imageId -contains $imgId } -CheckDesc 'filters by package'

Test-Api -Name 'PUT /api/admin/gallery/{id} updates url + caption' -Method Put -Path "/api/admin/gallery/$imgId" `
    -Token $adminToken -Expect 200 `
    -Body @{ imageUrl = 'https://example.com/updated.jpg'; caption = 'Updated caption'; packageId = $pkgId } `
    -Check { param($r) $r.Json.imageUrl -eq 'https://example.com/updated.jpg' -and $r.Json.caption -eq 'Updated caption' } `
    -CheckDesc 'imageUrl now actually updated (was silently ignored)'

Test-Api -Name 'PUT /api/admin/gallery/{unknown} -> 404' -Method Put -Path '/api/admin/gallery/999999' `
    -Token $adminToken -Expect 404 -Body @{ caption = 'x' }

# ---------------------------------------------------------------- 5. journal

Section '5. Journal'

$draft = Test-Api -Name 'POST /api/admin/journal (draft) -> 201' -Method Post -Path '/api/admin/journal' `
    -Token $adminToken -Expect 201 `
    -Body @{ title = "Draft Post $($RunId)"; description = 'draft desc'; content = 'draft content'; coverImageUrl = 'https://example.com/c.jpg' } `
    -Check { param($r) $r.Json.status -eq 'DRAFT' -and -not $r.Json.publishedAt } -CheckDesc 'defaults to DRAFT with no publishedAt' -Capture
$draftId = $draft.Json.journalId

Test-Api -Name 'GET /api/journal/published hides drafts' -Method Get -Path '/api/journal/published' -Expect 200 `
    -Check { param($r) -not ($r.Json.journalId -contains $draftId) } -CheckDesc 'draft not in public list'
Test-Api -Name 'GET /api/journal/published/{draftId} -> 404' -Method Get -Path "/api/journal/published/$draftId" -Expect 404

$published = Test-Api -Name 'PATCH /api/admin/journal/{id}/publish' -Method Patch -Path "/api/admin/journal/$draftId/publish" `
    -Token $adminToken -Expect 200 `
    -Check { param($r) $r.Json.status -eq 'PUBLISHED' -and $r.Json.publishedAt } -CheckDesc 'publishedAt set' -Capture
Test-Api -Name 'GET /api/journal/published/{id} after publish -> 200' -Method Get -Path "/api/journal/published/$draftId" -Expect 200
Test-Api -Name 'GET /api/journal/published now lists the post' -Method Get -Path '/api/journal/published' -Expect 200 `
    -Check { param($r) $r.Json.journalId -contains $draftId } -CheckDesc 'post visible publicly'

Test-Api -Name 'PUT /api/admin/journal/{id} back to DRAFT clears publishedAt' -Method Put -Path "/api/admin/journal/$draftId" `
    -Token $adminToken -Expect 200 `
    -Body @{ title = "Draft Post $($RunId)"; description = 'edited'; content = 'edited content'; status = 'draft' } `
    -Check { param($r) $r.Json.status -eq 'DRAFT' -and -not $r.Json.publishedAt } -CheckDesc 'normalized status + publishedAt cleared'

Test-Api -Name 'PATCH /api/admin/journal/{id}/unpublish' -Method Patch -Path "/api/admin/journal/$draftId/unpublish" `
    -Token $adminToken -Expect 200 -Check { param($r) $r.Json.status -eq 'DRAFT' } -CheckDesc 'status DRAFT'

Test-Api -Name 'GET /api/admin/journal (admin) -> 200' -Method Get -Path '/api/admin/journal' -Token $adminToken -Expect 200 `
    -Check { param($r) $r.Json.journalId -contains $draftId } -CheckDesc 'admin sees drafts too'
Test-Api -Name 'GET /api/admin/journal/{id} (admin)' -Method Get -Path "/api/admin/journal/$draftId" -Token $adminToken -Expect 200
Test-Api -Name 'GET /api/admin/journal (no token) -> 401' -Method Get -Path '/api/admin/journal' -Expect 401
Test-Api -Name 'POST /api/admin/journal invalid payload -> 400' -Method Post -Path '/api/admin/journal' `
    -Token $adminToken -Expect 400 -Body @{ title = ''; content = '' }

$tmpPost = Test-Api -Name 'POST /api/admin/journal (for delete test)' -Method Post -Path '/api/admin/journal' `
    -Token $adminToken -Expect 201 -Body @{ title = "Temp Post $($RunId)"; content = 'temp' } -Capture
Test-Api -Name 'DELETE /api/admin/journal/{id} -> 204' -Method Delete -Path "/api/admin/journal/$($tmpPost.Json.journalId)" `
    -Token $adminToken -Expect 204
Test-Api -Name 'GET deleted journal post -> 404' -Method Get -Path "/api/admin/journal/$($tmpPost.Json.journalId)" `
    -Token $adminToken -Expect 404

# ---------------------------------------------------------------- 6. contact / queries

Section '6. Contact queries'

$query = Test-Api -Name 'POST /api/contact (public guest query) -> 201' -Method Post -Path '/api/contact' `
    -Body @{ name = 'Guest User'; email = "guest-$($RunId)@example.com"; phone = '0770000000'; `
             subject = "Question $($RunId)"; message = 'Do you have availability in December?' } -Expect 201 `
    -Check { param($r) $r.Json.status -eq 'NEW' } -CheckDesc 'saved as NEW' -Capture
$queryId = $query.Json.queryId

Test-Api -Name 'POST /api/contact logged-in customer query -> 201' -Method Post -Path '/api/contact' `
    -Token $custAToken `
    -Body @{ customerId = $custAId; name = 'Customer A'; email = $custAEmail; subject = 'Linked query'; message = 'Linked to my account' } `
    -Expect 201 -Check { param($r) $r.Json.email -eq $custAEmail } -CheckDesc 'query stored'

Test-Api -Name 'POST /api/contact invalid payload -> 400' -Method Post -Path '/api/contact' `
    -Body @{ name = 'No Subject'; email = 'x@y.com'; message = 'hi' } -Expect 400

Test-Api -Name 'GET /api/admin/queries (admin)' -Method Get -Path '/api/admin/queries' -Token $adminToken -Expect 200 `
    -Check { param($r) $r.Json.queryId -contains $queryId } -CheckDesc 'new query is listed'
Test-Api -Name 'GET /api/admin/queries?onlyNew=true (admin)' -Method Get -Path '/api/admin/queries?onlyNew=true' `
    -Token $adminToken -Expect 200 -Check { param($r) $r.Json.queryId -contains $queryId } -CheckDesc 'query is NEW'

# Mail is sent asynchronously now, so the flag is written by the mail worker after the response.
Test-Condition -Name 'async mail: autoResponseSent flips to true after the response' -TimeoutSeconds 20 -CheckDesc 'mail worker updated the query' -Check {
    $r = Invoke-Api -Method Get -Path '/api/admin/queries' -Token $adminToken
    ($r.Json | Where-Object { $_.queryId -eq $queryId }).autoResponseSent -eq $true
}

Test-Api -Name 'GET /api/admin/queries (no token) -> 401' -Method Get -Path '/api/admin/queries' -Expect 401
Test-Api -Name 'GET /api/admin/queries (customer token) -> 403' -Method Get -Path '/api/admin/queries' `
    -Token $custAToken -Expect 403

$responded = Test-Api -Name 'PATCH /api/admin/queries/{id}/respond (admin)' -Method Patch `
    -Path "/api/admin/queries/$queryId/respond" -Token $adminToken -Expect 200 `
    -Body @{ adminResponse = 'Yes, December is available.' } `
    -Check { param($r) $r.Json.status -eq 'RESPONDED' -and $r.Json.adminResponse -and $r.Json.respondedByName } `
    -CheckDesc 'response + responder recorded' -Capture
Test-Api -Name 'PATCH respond twice -> 400' -Method Patch -Path "/api/admin/queries/$queryId/respond" `
    -Token $adminToken -Expect 400 -Body @{ adminResponse = 'again' }
Test-Api -Name 'PATCH respond to unknown query -> 404' -Method Patch -Path '/api/admin/queries/999999/respond' `
    -Token $adminToken -Expect 404 -Body @{ adminResponse = 'x' }
Test-Api -Name 'PATCH respond with blank text -> 400' -Method Patch -Path '/api/admin/queries/999999/respond' `
    -Token $adminToken -Expect 400 -Body @{ adminResponse = '' }

# ---------------------------------------------------------------- 7. bookings

Section '7. Bookings'

$booking = Test-Api -Name 'POST /api/bookings/request (customer A, own id) -> 201' -Method Post -Path '/api/bookings/request' `
    -Token $custAToken `
    -Body @{ customerId = $custAId; packageId = $pkgId; numTravelers = 2; preferredTravelDate = '2026-12-01'; specialRequests = 'Window seat please' } `
    -Expect 201 `
    -Check { param($r) $r.Json.status -eq 'PENDING' -and $r.Json.pinCode.Length -eq 8 -and $r.Json.customerEmail -eq $custAEmail } `
    -CheckDesc 'PENDING booking with 8-char PIN' -Capture
$bookingId = $booking.Json.bookingId
$bookingPin = $booking.Json.pinCode

Test-Api -Name 'POST /api/bookings/request without token -> 401 (was 201!)' -Method Post -Path '/api/bookings/request' `
    -Body @{ customerId = $custAId; packageId = $pkgId; numTravelers = 1; preferredTravelDate = '2026-12-02' } -Expect 401
Test-Api -Name 'POST booking for ANOTHER customer -> 403 (was 201!)' -Method Post -Path '/api/bookings/request' `
    -Token $custBToken `
    -Body @{ customerId = $custAId; packageId = $pkgId; numTravelers = 1; preferredTravelDate = '2026-12-02' } -Expect 403 `
    -Check { param($r) $r.Json.error -eq 'Forbidden' } -CheckDesc 'ownership enforced'
$bookingB = Test-Api -Name 'POST booking without customerId uses token identity -> 201' -Method Post -Path '/api/bookings/request' `
    -Token $custBToken -Body @{ packageId = $pkgId; numTravelers = 1; preferredTravelDate = '2027-01-15' } -Expect 201 `
    -Check { param($r) $r.Json.customerEmail -eq $custBEmail } -CheckDesc 'booked for the token owner' -Capture
$bookingBId = $bookingB.Json.bookingId
Test-Api -Name 'POST booking for admin account -> 403' -Method Post -Path '/api/bookings/request' `
    -Token $adminToken -Body @{ packageId = $pkgId; numTravelers = 1; preferredTravelDate = '2027-01-15' } -Expect 403

Test-Api -Name 'POST booking on INACTIVE package -> 400' -Method Post -Path '/api/bookings/request' -Token $custAToken `
    -Body @{ packageId = $deadPkgId; numTravelers = 1; preferredTravelDate = '2026-12-05' } -Expect 400 `
    -Check { param($r) $r.Json.message -like '*not available*' } -CheckDesc 'package availability enforced'
Test-Api -Name 'POST booking over capacity -> 400' -Method Post -Path '/api/bookings/request' -Token $custAToken `
    -Body @{ packageId = $smallPkgId; numTravelers = 5; preferredTravelDate = '2026-12-05' } -Expect 400 `
    -Check { param($r) $r.Json.message -like '*capacity*' } -CheckDesc 'capacity enforced'
Test-Api -Name 'POST booking with unknown package -> 404' -Method Post -Path '/api/bookings/request' -Token $custAToken `
    -Body @{ packageId = 999999; numTravelers = 1; preferredTravelDate = '2026-12-05' } -Expect 404
Test-Api -Name 'POST booking with invalid payload -> 400' -Method Post -Path '/api/bookings/request' -Token $custAToken `
    -Body @{ packageId = $pkgId; numTravelers = 0; preferredTravelDate = 'not-a-date' } -Expect 400

Test-Api -Name 'GET /api/bookings/track?pin={pin} (public)' -Method Get -Path "/api/bookings/track?pin=$bookingPin" -Expect 200 `
    -Check { param($r) $r.Json.bookingId -eq $bookingId } -CheckDesc 'tracked by PIN'
Test-Api -Name 'GET /api/bookings/track with unknown pin -> 404' -Method Get -Path '/api/bookings/track?pin=ZZZZZZZZ' -Expect 404
Test-Api -Name 'GET /api/bookings/track without pin -> 400 (was 500)' -Method Get -Path '/api/bookings/track' -Expect 400

Test-Api -Name 'GET /api/customer/bookings (customer A sees own)' -Method Get -Path '/api/customer/bookings' `
    -Token $custAToken -Expect 200 `
    -Check { param($r) ($r.Json.bookingId -contains $bookingId) -and -not ($r.Json.bookingId -contains $bookingBId) } `
    -CheckDesc 'only own bookings returned (was a full-table scan)'
Test-Api -Name 'GET /api/customer/bookings (admin token) -> 403' -Method Get -Path '/api/customer/bookings' `
    -Token $adminToken -Expect 403

Test-Api -Name 'GET /api/admin/bookings (admin)' -Method Get -Path '/api/admin/bookings' -Token $adminToken -Expect 200 `
    -Check { param($r) $r.Json.bookingId -contains $bookingId } -CheckDesc 'all bookings visible to admin'
Test-Api -Name 'GET /api/admin/bookings?status=PENDING (admin)' -Method Get -Path '/api/admin/bookings?status=PENDING' `
    -Token $adminToken -Expect 200 -Check { param($r) $r.Json.status -contains 'PENDING' } -CheckDesc 'filtered by status'
Test-Api -Name 'GET /api/admin/bookings?status=BOGUS -> 400' -Method Get -Path '/api/admin/bookings?status=BOGUS' `
    -Token $adminToken -Expect 400
Test-Api -Name 'GET /api/admin/bookings (no token) -> 401' -Method Get -Path '/api/admin/bookings' -Expect 401

Test-Api -Name 'PATCH confirm booking without token -> 401' -Method Patch -Path "/api/admin/bookings/$bookingId/confirm" `
    -Expect 401 -Body @{ confirmedPrice = 300; confirmedDate = '2026-11-20' }

$confirmed = Test-Api -Name 'PATCH /api/admin/bookings/{id}/confirm (admin)' -Method Patch `
    -Path "/api/admin/bookings/$bookingId/confirm" -Token $adminToken -Expect 200 `
    -Body @{ confirmedPrice = 399.98; confirmedDate = '2026-11-20' } `
    -Check { param($r) $r.Json.status -eq 'CONFIRMED' -and $r.Json.confirmedPrice -eq 399.98 } -CheckDesc 'price + date persisted' -Capture
Test-Api -Name 'PATCH confirm again -> 400' -Method Patch -Path "/api/admin/bookings/$bookingId/confirm" `
    -Token $adminToken -Expect 400 -Body @{ confirmedPrice = 100; confirmedDate = '2026-11-21' }
Test-Api -Name 'PATCH reject a CONFIRMED booking -> 400' -Method Patch -Path "/api/admin/bookings/$bookingId/reject" `
    -Token $adminToken -Expect 400 -Check { param($r) $r.Json.message -like '*pending*' } -CheckDesc 'only pending can be rejected'
Test-Api -Name 'PATCH confirm with invalid payload -> 400' -Method Patch -Path "/api/admin/bookings/$bookingBId/confirm" `
    -Token $adminToken -Expect 400 -Body @{ confirmedPrice = -5; confirmedDate = '2026-11-21' }

Test-Api -Name 'PATCH /api/admin/bookings/{id}/reject (admin)' -Method Patch -Path "/api/admin/bookings/$bookingBId/reject" `
    -Token $adminToken -Expect 200 -Check { param($r) $r.Json.status -eq 'REJECTED' } -CheckDesc 'booking rejected'
Test-Api -Name 'PATCH confirm a REJECTED booking -> 400' -Method Patch -Path "/api/admin/bookings/$bookingBId/confirm" `
    -Token $adminToken -Expect 400 -Body @{ confirmedPrice = 100; confirmedDate = '2026-11-21' }
Test-Api -Name 'PATCH confirm unknown booking -> 404' -Method Patch -Path '/api/admin/bookings/999999/confirm' `
    -Token $adminToken -Expect 404 -Body @{ confirmedPrice = 100; confirmedDate = '2026-11-21' }

# ---------------------------------------------------------------- 8. reviews

Section '8. Reviews'

$review = Test-Api -Name 'POST /api/reviews (customer A, own id) -> 201' -Method Post -Path '/api/reviews' `
    -Token $custAToken -Body @{ customerId = $custAId; packageId = $pkgId; rating = 5; comment = "Great trip $($RunId)" } `
    -Expect 201 -Check { param($r) $r.Json.rating -eq 5 -and $r.Json.customerEmail -eq $null } -CheckDesc 'review stored' -Capture
$reviewId = $review.Json.reviewId

Test-Api -Name 'POST /api/reviews without token -> 401 (was 201!)' -Method Post -Path '/api/reviews' `
    -Body @{ customerId = $custAId; packageId = $pkgId; rating = 1; comment = 'anonymous spoof' } -Expect 401
Test-Api -Name 'POST review AS another customer -> 403 (was 201!)' -Method Post -Path '/api/reviews' `
    -Token $custBToken -Body @{ customerId = $custAId; packageId = $pkgId; rating = 1; comment = 'spoofed by B' } -Expect 403 `
    -Check { param($r) $r.Json.error -eq 'Forbidden' } -CheckDesc 'review spoofing blocked'
$generalReview = Test-Api -Name 'POST /api/reviews general company review (no package) -> 201' -Method Post -Path '/api/reviews' `
    -Token $custBToken -Body @{ rating = 4; comment = 'Nice service' } -Expect 201 `
    -Check { param($r) $r.Json.customerId -eq $custBId } -CheckDesc 'attributed to token owner' -Capture
$generalReviewId = $generalReview.Json.reviewId
Test-Api -Name 'POST /api/reviews with rating 9 -> 400' -Method Post -Path '/api/reviews' `
    -Token $custAToken -Body @{ packageId = $pkgId; rating = 9; comment = 'x' } -Expect 400

Test-Api -Name 'GET /api/reviews (public)' -Method Get -Path '/api/reviews' -Expect 200 `
    -Check { param($r) $r.Json.reviewId -contains $reviewId } -CheckDesc 'review listed'
Test-Api -Name 'GET /api/reviews/package/{id} (public)' -Method Get -Path "/api/reviews/package/$pkgId" -Expect 200 `
    -Check { param($r) $r.Json.reviewId -contains $reviewId } -CheckDesc 'filtered by package'
Test-Api -Name 'GET /api/reviews/package/{unknown} -> 200 empty' -Method Get -Path '/api/reviews/package/999999' -Expect 200 `
    -Check { param($r) $r.Json.Count -eq 0 } -CheckDesc 'empty list, not an error'

Test-Api -Name 'DELETE /api/admin/reviews/{id} (customer token) -> 403' -Method Delete -Path "/api/admin/reviews/$reviewId" `
    -Token $custAToken -Expect 403
Test-Api -Name 'DELETE /api/admin/reviews/{id} (admin) -> 204' -Method Delete -Path "/api/admin/reviews/$reviewId" `
    -Token $adminToken -Expect 204
Test-Api -Name 'DELETE same review twice -> 404' -Method Delete -Path "/api/admin/reviews/$reviewId" `
    -Token $adminToken -Expect 404

# ---------------------------------------------------------------- 9. concurrency

Section '9. Concurrency (capacity lock + optimistic locking)'

# A package whose entire capacity is contended: 5 parallel requests for 3 seats.
$racePkg = Test-Api -Name 'POST /api/admin/packages (capacity 3, concurrency test)' -Method Post -Path '/api/admin/packages' `
    -Token $adminToken -Expect 201 `
    -Body @{ title = "Race Package $($RunId)"; destination = 'Raceland'; durationDays = 2; price = 100; maxCapacity = 3 } -Capture
$racePkgId = $racePkg.Json.packageId

$raceResults = Invoke-Concurrent -Method Post -Path '/api/bookings/request' -Token $custAToken -Count 5 `
    -Body @{ packageId = $racePkgId; numTravelers = 1; preferredTravelDate = '2027-03-01' }
$raceCodes = @($raceResults | ForEach-Object { $_.Status })
$accepted  = @($raceCodes | Where-Object { $_ -eq 201 }).Count
$refused   = @($raceCodes | Where-Object { $_ -eq 400 -or $_ -eq 409 }).Count
$errors    = @($raceCodes | Where-Object { $_ -ge 500 }).Count

Test-Condition -Name 'capacity lock: 5 parallel bookings produce no 5xx' -CheckDesc "codes: $($raceCodes -join ',')" `
    -Check { $errors -eq 0 }
Test-Condition -Name 'capacity lock: exactly the free seats are accepted' -CheckDesc "$accepted accepted, $refused refused" `
    -Check { $accepted -eq 3 -and $refused -eq 2 }

Test-Api -Name 'capacity lock: outstanding travelers never exceed capacity' -Method Get -Path '/api/admin/bookings' `
    -Token $adminToken -Expect 200 `
    -Check { param($r) (($r.Json | Where-Object { $_.destination -eq 'Raceland' -and $_.status -ne 'REJECTED' } | Measure-Object -Property numTravelers -Sum).Sum) -le 3 } `
    -CheckDesc 'sum of outstanding travelers <= maxCapacity'

# Optimistic locking: two admins confirming the same booking at the same moment.
$raceBooking = Test-Api -Name 'POST booking (for double-confirm race)' -Method Post -Path '/api/bookings/request' `
    -Token $custAToken -Expect 201 `
    -Body @{ packageId = $pkgId; numTravelers = 1; preferredTravelDate = '2027-04-01' } -Capture
$confirmResults = Invoke-Concurrent -Method Patch -Path "/api/admin/bookings/$($raceBooking.Json.bookingId)/confirm" `
    -Token $adminToken -Count 2 -Body @{ confirmedPrice = 150; confirmedDate = '2027-03-20' }
$confirmCodes = @($confirmResults | ForEach-Object { $_.Status })
$wins   = @($confirmCodes | Where-Object { $_ -eq 200 }).Count
$losses = @($confirmCodes | Where-Object { $_ -eq 400 -or $_ -eq 409 }).Count

Test-Condition -Name 'optimistic lock: exactly one of two parallel confirms wins' -CheckDesc "codes: $($confirmCodes -join ',')" `
    -Check { $wins -eq 1 -and $losses -eq 1 }
Test-Api -Name 'booking is CONFIRMED exactly once after the race' -Method Get -Path '/api/admin/bookings?status=CONFIRMED' `
    -Token $adminToken -Expect 200 `
    -Check { param($r) @($r.Json | Where-Object { $_.bookingId -eq $raceBooking.Json.bookingId }).Count -eq 1 } `
    -CheckDesc 'no duplicate confirmation'

# ---------------------------------------------------------------- 10. cleanup

Section '10. Cleanup (data created by this run)'

Test-Api -Name 'DELETE package with bookings -> 409 conflict' -Method Delete -Path "/api/admin/packages/$pkgId" `
    -Token $adminToken -Expect 409 -Check { param($r) $r.Json.error -eq 'Conflict' } -CheckDesc 'FK conflict reported as 409 (was 500)'

Test-Api -Name 'DELETE unused package -> 204' -Method Delete -Path "/api/admin/packages/$smallPkgId" -Token $adminToken -Expect 204
Test-Api -Name 'DELETE /api/admin/gallery/{id} -> 204' -Method Delete -Path "/api/admin/gallery/$imgId" -Token $adminToken -Expect 204
Test-Api -Name 'DELETE /api/admin/journal/{id} -> 204' -Method Delete -Path "/api/admin/journal/$draftId" -Token $adminToken -Expect 204
Test-Api -Name 'DELETE general review -> 204' -Method Delete -Path "/api/admin/reviews/$generalReviewId" -Token $adminToken -Expect 204

# ---------------------------------------------------------------- summary

$total = $script:Pass + $script:Fail
Write-Host ''
Write-Host ('=' * 72) -ForegroundColor Cyan
Write-Host ("RESULT: {0}/{1} checks passed, {2} failed" -f $script:Pass, $total, $script:Fail) -ForegroundColor $(if ($script:Fail -eq 0) { 'Green' } else { 'Red' })

if ($script:Fail -gt 0) {
    Write-Host ''
    Write-Host 'Failures:' -ForegroundColor Red
    foreach ($f in $script:Failures) { Write-Host "  - $f" -ForegroundColor Red }
}

$script:Http.Dispose()
Write-Host ('=' * 72) -ForegroundColor Cyan
if ($script:Fail -gt 0) { exit 1 } else { exit 0 }
