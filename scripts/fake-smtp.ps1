<#
.SYNOPSIS
    Minimal fake SMTP server for local testing.

.DESCRIPTION
    Listens on 127.0.0.1:2525, accepts every message and prints the envelope. It exists so the
    mail-sending code paths (booking pending/confirmation mails, contact auto-response) can be
    exercised without an external SMTP provider.

    No authentication is implemented - start the application with:

        --spring.mail.host=127.0.0.1 --spring.mail.port=2525
        --spring.mail.properties.mail.smtp.auth=false

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/fake-smtp.ps1 -Port 2525

.PARAMETER Dump
    Optional file to append each received message to, headers included, so what the application
    actually sent (sender, recipient, subject) can be asserted. CI uses this to prove the configured
    From address reaches the wire; leave it out for a quiet sink.
#>
[CmdletBinding()]
param(
    [int]$Port = 2525,
    [string]$Dump
)

$ErrorActionPreference = 'Stop'

if ($Dump) {
    $dumpPath = [System.IO.Path]::GetFullPath($Dump)
    Set-Content -Path $dumpPath -Value "fake-smtp dump opened $(Get-Date -Format o)" -Encoding UTF8
    Write-Host "fake-smtp: writing messages to $dumpPath"
}

$listener = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, $Port)
$listener.Start()
Write-Host "fake-smtp: listening on 127.0.0.1:$Port"

while ($true) {
    $client = $listener.AcceptTcpClient()
    try {
        $stream = $client.GetStream()
        $reader = New-Object System.IO.StreamReader($stream)
        $writer = New-Object System.IO.StreamWriter($stream)
        $writer.NewLine = "`r`n"
        $writer.AutoFlush = $true

        $writer.WriteLine('220 fake-smtp ready')
        $inData = $false
        $from = ''
        $to = ''
        $lines = New-Object System.Collections.ArrayList

        while ($true) {
            $line = $reader.ReadLine()
            if ($null -eq $line) { break }

            if ($inData) {
                if ($line -eq '.') {
                    $inData = $false
                    $writer.WriteLine('250 OK queued')
                    Write-Host "fake-smtp: accepted message from $from to $to"
                    if ($Dump) {
                        $record = "===== MESSAGE =====`r`n$from`r`n$to`r`n" + ($lines -join "`r`n")
                        Add-Content -Path $dumpPath -Value $record -Encoding UTF8
                    }
                    continue
                }
                # undo SMTP dot-stuffing so the dumped body matches what was sent
                [void]$lines.Add(($line -replace '^\.\.', '.'))
                continue
            }

            $upper = $line.ToUpperInvariant()
            if ($upper.StartsWith('EHLO')) {
                $writer.WriteLine('250-fake-smtp')
                $writer.WriteLine('250 SIZE 10485760')
            }
            elseif ($upper.StartsWith('HELO')) { $writer.WriteLine('250 fake-smtp') }
            elseif ($upper.StartsWith('MAIL FROM')) { $from = $line; $writer.WriteLine('250 OK') }
            elseif ($upper.StartsWith('RCPT TO')) { $to = $line; $writer.WriteLine('250 OK') }
            elseif ($upper -eq 'DATA') { $writer.WriteLine('354 End data with <CR><LF>.<CR><LF>'); $inData = $true }
            elseif ($upper.StartsWith('QUIT')) { $writer.WriteLine('221 Bye'); break }
            else { $writer.WriteLine('250 OK') }
        }
    }
    catch {
        # A client that connects and drops the socket (health checks, port probes, ...) must not
        # take the whole sink down.
        Write-Host "fake-smtp: connection dropped: $($_.Exception.Message)"
    }
    finally {
        $client.Close()
    }
}