<#
Bounded, account-free reachability check for Minecraft servers. For each server
it resolves DNS, opens one TCP connection, and sends a Minecraft 1.8.9
(protocol 47) status request: the same exchange as the multiplayer server list.
It sends no username, no account data and no login, so a failure here cannot be
caused by the Microsoft account, Lunar's account state or ATW's game agents.

One attempt per server, a fixed timeout, no retries. Works in Windows
PowerShell 5.1 and PowerShell 7. Example:
  powershell -NoProfile -ExecutionPolicy Bypass -File diagnose_server_reachability.ps1 `
      -Servers mc.hypixel.net,bedwarspractice.club

Result per server:
  status_ok          the server answered with its version and player count
  reset_before_status the connection was reset after connecting, before any
                     answer (seen for Hypixel from this home network)
  closed_before_status the server closed the connection without answering
  timeout / tcp_failed / dns_failed
#>
param(
    [string[]]$Servers = @('mc.hypixel.net', 'bedwarspractice.club', 'na.minemen.club'),
    [int]$TimeoutMs = 8000
)
$ErrorActionPreference = 'Stop'

function Write-VarInt([IO.Stream]$stream, [int]$value) {
    $v = [uint32]$value
    do {
        $byte = [byte]($v -band 0x7F)
        $v = $v -shr 7
        if ($v -ne 0) { $byte = $byte -bor 0x80 }
        $stream.WriteByte($byte)
    } while ($v -ne 0)
}

function Read-VarInt([IO.Stream]$stream) {
    $value = 0; $shift = 0
    while ($true) {
        $b = $stream.ReadByte()
        if ($b -lt 0) { throw [IO.EndOfStreamException]::new('closed') }
        $value = $value -bor (($b -band 0x7F) -shl $shift)
        if (($b -band 0x80) -eq 0) { return $value }
        $shift += 7
        if ($shift -gt 35) { throw 'bad varint' }
    }
}

function New-Packet([int]$id, [byte[]]$payload) {
    $body = New-Object IO.MemoryStream
    Write-VarInt $body $id
    $body.Write($payload, 0, $payload.Length)
    $packet = New-Object IO.MemoryStream
    Write-VarInt $packet ([int]$body.Length)
    $body.WriteTo($packet)
    return , $packet.ToArray()
}

function Test-Server([string]$server) {
    $hostName, $portText = $server.Split(':')
    $port = if ($portText) { [int]$portText } else { 25565 }
    $result = [ordered]@{ server = "${hostName}:$port"; result = ''; address = ''; connectMs = ''; detail = '' }
    try {
        $address = [Net.Dns]::GetHostAddresses($hostName) | Where-Object { $_.AddressFamily -eq 'InterNetwork' } | Select-Object -First 1
        if (!$address) { throw 'no IPv4 address' }
        $result.address = $address.ToString()
    } catch { $result.result = 'dns_failed'; $result.detail = $_.Exception.Message; return [pscustomobject]$result }

    $client = New-Object Net.Sockets.TcpClient
    try {
        $timer = [Diagnostics.Stopwatch]::StartNew()
        $connect = $client.BeginConnect($address, $port, $null, $null)
        if (!$connect.AsyncWaitHandle.WaitOne($TimeoutMs) -or !$client.Connected) {
            $result.result = 'tcp_failed'; $result.detail = "no TCP connection within $TimeoutMs ms"
            return [pscustomobject]$result
        }
        $client.EndConnect($connect)
        $result.connectMs = $timer.ElapsedMilliseconds
        $stream = $client.GetStream()
        $stream.ReadTimeout = $TimeoutMs
        $stream.WriteTimeout = $TimeoutMs

        # Handshake: protocol 47, server address, port, next state 1 (status).
        $hs = New-Object IO.MemoryStream
        Write-VarInt $hs 47
        $nameBytes = [Text.Encoding]::UTF8.GetBytes($hostName)
        Write-VarInt $hs $nameBytes.Length
        $hs.Write($nameBytes, 0, $nameBytes.Length)
        $hs.WriteByte([byte](($port -shr 8) -band 0xFF)); $hs.WriteByte([byte]($port -band 0xFF))
        Write-VarInt $hs 1
        $handshake = New-Packet 0 $hs.ToArray()
        $request = New-Packet 0 ([byte[]]@())
        $stream.Write($handshake, 0, $handshake.Length)
        $stream.Write($request, 0, $request.Length)

        $length = Read-VarInt $stream
        $packetId = Read-VarInt $stream
        $jsonLength = Read-VarInt $stream
        if ($packetId -ne 0 -or $jsonLength -le 0 -or $jsonLength -gt $length) { throw 'unexpected status packet' }
        $buffer = New-Object byte[] $jsonLength
        $read = 0
        while ($read -lt $jsonLength) {
            $n = $stream.Read($buffer, $read, $jsonLength - $read)
            if ($n -le 0) { throw [IO.EndOfStreamException]::new('closed') }
            $read += $n
        }
        $status = [Text.Encoding]::UTF8.GetString($buffer) | ConvertFrom-Json
        $result.result = 'status_ok'
        $result.detail = "version '$($status.version.name)' protocol $($status.version.protocol), players $($status.players.online)/$($status.players.max)"
    } catch {
        $e = $_.Exception
        while ($e.InnerException) { $e = $e.InnerException }
        if ($e -is [Net.Sockets.SocketException] -and $e.SocketErrorCode -eq 'ConnectionReset') { $result.result = 'reset_before_status' }
        elseif ($e -is [Net.Sockets.SocketException] -and $e.SocketErrorCode -eq 'TimedOut') { $result.result = 'timeout' }
        elseif ($e -is [IO.EndOfStreamException]) { $result.result = 'closed_before_status' }
        elseif ($e -is [IO.IOException] -and $e.Message -match 'timed out|period of time') { $result.result = 'timeout' }
        else { $result.result = 'error' }
        $result.detail = $e.GetType().Name + ': ' + $e.Message
    } finally {
        $client.Close()
    }
    return [pscustomobject]$result
}

$exit = 0
"computer=$env:COMPUTERNAME  time=$((Get-Date).ToString('s'))  powershell=$($PSVersionTable.PSVersion)"
foreach ($server in $Servers) {
    $r = Test-Server $server
    if ($r.result -ne 'status_ok') { $exit = 1 }
    '{0,-28} {1,-21} ip={2,-15} connect={3,-5}ms {4}' -f $r.server, $r.result, $r.address, $r.connectMs, $r.detail
}
exit $exit
