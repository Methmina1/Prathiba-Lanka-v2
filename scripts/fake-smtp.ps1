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
#>
[CmdletBinding()]
param([int]$Port = 2525)

$ErrorActionPreference = 'Stop'

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

        while ($true) {
            $line = $reader.ReadLine()
            if ($null -eq $line) { break }

            if ($inData) {
                if ($line -eq '.') {
                    $inData = $false
                    $writer.WriteLine('250 OK queued')
                    Write-Host "fake-smtp: accepted message from $from to $to"
                }
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