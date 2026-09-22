<#
.SYNOPSIS
    Proves that the enquiry mails leave the application, over a real SMTP conversation.

.DESCRIPTION
    Two things are checked, both against a running instance pointed at a mail sink (scripts/fake-smtp.ps1
    writes every message it accepts to -Dump):

      1. The configured From address reaches the wire, on the header and on the SMTP envelope.
      2. The console's reply reaches the customer: stored on the enquiry, mailed with its reference in
         the subject, addressed to the person who asked, carrying the reply text, and holding a link
         that really opens that customer's own enquiry.

    Point 2 exists because the reply used to be written into the database and never sent - the customer's
    only answer was silence, while the console said "saved". A check that only reads the API would pass
    with that bug present, so this one reads what the mail server was actually handed.

.PARAMETER BaseUrl
    A RUNNING backend. Default: http://localhost:8080

.PARAMETER Dump
    The sink's dump file, as passed to fake-smtp.ps1 -Dump. Default: smtp-dump.txt

.PARAMETER Recipient
    The address to enquire from. It should be one that cannot receive mail: this sends real messages to
    the sink, not to a person, and the address only has to be recognisable in the dump.

.PARAMETER TimeoutSeconds
    How long to wait for each message to appear in the dump. Mail is sent after the transaction commits,
    on a worker thread, so it is never there the instant the API answers.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/verify-mail.ps1 -BaseUrl http://localhost:8080

.NOTES
    The admin password comes from BOOTSTRAP_ADMIN_PASSWORD, the same variable the app creates the account
    from. Exit code 0 = every check passed, 1 = at least one failed.
#>
[CmdletBinding()]
param(
    [string]$BaseUrl        = 'http://localhost:8080',
    [string]$Dump           = 'smtp-dump.txt',
    [string]$Recipient      = 'ci@example.com',
    [string]$AdminEmail     = $(if ($env:BOOTSTRAP_ADMIN_EMAIL) { $env:BOOTSTRAP_ADMIN_EMAIL } else { 'prathibhalankavoyages@gmail.com' }),
    [string]$AdminPassword  = $(if ($env:PRATHIBALANKA_ADMIN_PASSWORD) { $env:PRATHIBALANKA_ADMIN_PASSWORD } elseif ($env:BOOTSTRAP_ADMIN_PASSWORD) { $env:BOOTSTRAP_ADMIN_PASSWORD } else { '' }),
    [string]$ExpectFrom     = $(if ($env:MAIL_FROM) { $env:MAIL_FROM } else { 'prathibhalankavoyages@gmail.com' }),
    [string]$ExpectFromName = $(if ($env:MAIL_FROM_NAME) { $env:MAIL_FROM_NAME } else { 'PrathibaLanka' }),
    [int]$TimeoutSeconds    = 25
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($AdminPassword)) {
    Write-Host 'No admin password: set BOOTSTRAP_ADMIN_PASSWORD (the variable the app creates the admin from).' -ForegroundColor Red
    exit 1
}

$script:Pass = 0
$script:Fail = 0

function Check {
    param([string]$Name, [scriptblock]$Condition, [string]$Detail = '')
    $ok = $false
    try { $ok = [bool](& $Condition) } catch { $ok = $false; $Detail = $_.Exception.Message }
    if ($ok) {
        $script:Pass++
        Write-Host ("  [PASS] {0}" -f $Name) -ForegroundColor Green
    } else {
        $script:Fail++
        Write-Host ("  [FAIL] {0} {1}" -f $Name, $Detail) -ForegroundColor Red
    }
}

function Invoke-Json {
    param([string]$Method, [string]$Path, $Body, [string]$Token)
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $args = @{ Uri = "$BaseUrl$Path"; Method = $Method; Headers = $headers; UseBasicParsing = $true }
    if ($null -ne $Body) {
        $args['ContentType'] = 'application/json'
        $args['Body'] = ($Body | ConvertTo-Json -Depth 6)
    }
    try {
        $response = Invoke-WebRequest @args
        return @{ Status = [int]$response.StatusCode; Json = ($response.Content | ConvertFrom-Json); Text = $response.Content }
    } catch {
        $status = -1
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
        return @{ Status = $status; Json = $null; Text = $_.ErrorDetails.Message }
    }
}

# The dump is appended to as messages arrive, so it is read fresh every time rather than cached.
#
# Read through a handle that shares with the writer, not with Get-Content. The sink process may hold the
# file open for the moment it takes to append a message, and a plain read does not fail loudly there - it
# returns nothing at all, so a wait loop would sit through its whole timeout seeing an empty file while
# the message it was waiting for was already on disk.
function Get-Dump {
    if (-not (Test-Path -LiteralPath $Dump)) { return '' }
    $stream = $null
    $reader = $null
    try {
        $stream = [System.IO.File]::Open(
            [System.IO.Path]::GetFullPath($Dump),
            [System.IO.FileMode]::Open,
            [System.IO.FileAccess]::Read,
            [System.IO.FileShare]::ReadWrite)
        $reader = New-Object System.IO.StreamReader($stream)
        return $reader.ReadToEnd()
    } catch {
        # A read that cannot happen yet is not a failure: the wait loop tries again.
        return ''
    } finally {
        if ($reader) { $reader.Close() }
        if ($stream) { $stream.Close() }
    }
}

function Wait-ForDump {
    param([string]$Pattern, [int]$Seconds = $TimeoutSeconds)
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        $text = Get-Dump
        if ($text -match $Pattern) { return $true }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    return $false
}

# Everything in PowerShell 5.1 needs the assembly loaded explicitly; PowerShell 7 already has it.
try { Add-Type -AssemblyName System.Net.Http -ErrorAction Stop } catch { }

# The sink writes each message as one block, so a single message's headers and body can be looked at on
# their own. Checking against the whole dump instead would pass on a message from an earlier run - which
# is exactly how a check like this goes quietly useless.
function Get-Block {
    param([string]$Text, [string]$Marker)
    ($Text -split '===== MESSAGE =====' | Where-Object { $_ -match [regex]::Escape($Marker) }) -join "`n"
}

$dumpPath = [System.IO.Path]::GetFullPath($Dump)
Write-Host ''
Write-Host "Mail verification against $BaseUrl (sink dump: $dumpPath)" -ForegroundColor White
Write-Host ''

if (-not (Test-Path $dumpPath)) {
    Write-Host "No dump at $dumpPath - start scripts/fake-smtp.ps1 with -Dump $Dump and point the app at it." -ForegroundColor Red
    exit 1
}

# ---------------------------------------------------------------- 1. the sender

$enquiry = Invoke-Json -Method Post -Path '/api/contact' -Body @{
    name    = 'Mail Check'
    email   = $Recipient
    subject = 'Sender check'
    message = 'Checking that a reply reaches the person who asked.'
}

Check 'the enquiry is accepted' { $enquiry.Status -eq 201 } "expected 201, got $($enquiry.Status)"
$queryId = $enquiry.Json.queryId

# The reference in the body, not the subject: a subject carrying an en dash goes out MIME-encoded, and
# the reference is what makes this *this run's* message rather than one from an earlier run.
$reference = "Your enquiry reference: #$queryId"
$ackSeen = Wait-ForDump -Pattern ([regex]::Escape($reference))
Check 'the acknowledgement reaches the wire' { $ackSeen } "nothing for enquiry #$queryId in $TimeoutSeconds seconds"

$ackBlock = Get-Block (Get-Dump) $reference
Check 'the envelope sender is the configured address' { $ackBlock -match [regex]::Escape("MAIL FROM:<$ExpectFrom>") }
Check 'the From header carries the display name' { $ackBlock -match [regex]::Escape("From: $ExpectFromName <$ExpectFrom>") }

# ---------------------------------------------------------------- 2. the reply

$login = Invoke-Json -Method Post -Path '/api/auth/login' -Body @{ email = $AdminEmail; password = $AdminPassword }
Check 'the administrator can sign in' { $login.Json.token } "status $($login.Status)"
$token = $login.Json.token
if (-not $token) { Write-Host 'Cannot continue without an admin token.' -ForegroundColor Red; exit 1 }

$replyText = "Yes - this reply reaches you by email. (mail check $((Get-Date).ToString('HHmmss')))"
$respond = Invoke-Json -Method Patch -Path "/api/admin/queries/$queryId/respond" -Token $token -Body @{ adminResponse = $replyText }
Check 'the reply is stored on the enquiry' { $respond.Status -eq 200 -and $respond.Json.status -eq 'RESPONDED' } "status $($respond.Status)"

# Mail is sent after the commit, on the worker thread: nothing is in the dump the instant the API answers.
$replySeen = Wait-ForDump -Pattern ([regex]::Escape($replyText))
Check 'the reply reaches the customer by email' { $replySeen } "no reply for enquiry #$queryId within $TimeoutSeconds seconds"

$replyBlock = Get-Block (Get-Dump) $replyText
Check 'the reply is addressed to the person who asked' { $replyBlock -match [regex]::Escape("RCPT TO:<$Recipient>") }
Check 'the reply subject carries the reference' { $replyBlock -match [regex]::Escape("Subject: Re: [#$queryId] Sender check") }
Check 'the reply quotes what the customer asked' { $replyBlock -match 'Checking that a reply reaches the person who asked' }
Check 'the reply carries the identity of the sender' { $replyBlock -match [regex]::Escape("From: $ExpectFromName <$ExpectFrom>") }

# The link in the reply is the customer's whole credential, so it is opened here: a token that does not
# resolve would leave them with an email they cannot answer.
$tokenMatch = [regex]::Match($replyBlock, 'enquiry/([a-f0-9]{32})')
Check 'the reply carries a link to the customer''s own page' { $tokenMatch.Success }

if ($tokenMatch.Success) {
    $customer = Invoke-Json -Method Get -Path "/api/enquiries/$($tokenMatch.Groups[1].Value)"
    Check 'the link opens the enquiry it came from' { $customer.Status -eq 200 -and $customer.Json.queryId -eq $queryId } `
        "status $($customer.Status), enquiry $($customer.Json.queryId)"

    $followUp = 'One more thing, from the mail check.'
    $posted = Invoke-Json -Method Post -Path "/api/enquiries/$($tokenMatch.Groups[1].Value)/messages" `
        -Body @{ message = $followUp }
    Check 'the customer can write back on it' { $posted.Status -eq 201 -and $posted.Json.awaitingReply -eq $true } "status $($posted.Status)"

    # …and the agency inbox is told, with the customer as the reply-to so it can be answered from Gmail.
    $notified = Wait-ForDump -Pattern ([regex]::Escape($followUp))
    Check 'the agency is told the customer wrote again' { $notified } "no notification within $TimeoutSeconds seconds"

    $noteBlock = Get-Block (Get-Dump) $followUp
    Check 'that notification is answered back to the customer' { $noteBlock -match [regex]::Escape("Reply-To: $Recipient") } `
        'the agency note should carry the customer as its reply-to'
}

# ---------------------------------------------------------------- summary

$total = $script:Pass + $script:Fail
Write-Host ''
Write-Host ("RESULT: {0}/{1} mail checks passed, {2} failed" -f $script:Pass, $total, $script:Fail) `
    -ForegroundColor $(if ($script:Fail -eq 0) { 'Green' } else { 'Red' })
if ($script:Fail -gt 0) {
    Write-Host "Enquiry #$queryId was used for this run." -ForegroundColor Yellow
    exit 1
}
exit 0
