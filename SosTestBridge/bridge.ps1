# SosTestBridge client. Usage:  .\bridge.ps1 "status" [-TimeoutSec 30]
# Writes "<id> <command>" to in.txt, waits for the out.jsonl line with the same id and prints the result.
# Exit code: 0 = ok, 1 = error returned by the game, 2 = game is not alive, 3 = timeout.
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$Command,
    [int]$TimeoutSec = 30,
    [switch]$Raw   # print the response JSON unformatted
)
$ErrorActionPreference = 'Stop'
$dir    = Join-Path $env:APPDATA 'songsofsyx\bridge'
$in     = Join-Path $dir 'in.txt'
$out    = Join-Path $dir 'out.jsonl'
$status = Join-Path $dir 'status.json'

if ($Command -match "[\r\n]") { Write-Error 'command must not contain line breaks'; exit 1 }

# status.json is rewritten every second while the game runs; stale = game closed or at the menu
if (-not (Test-Path $status) -or ((Get-Date) - (Get-Item $status).LastWriteTime).TotalSeconds -gt 30) {
    Write-Error 'game is not alive (status.json missing or stale for more than 30 s)'
    exit 2
}

function Get-OutLength { if (Test-Path $out) { (Get-Item $out).Length } else { 0 } }

$id  = 'c' + [Guid]::NewGuid().ToString('N').Substring(0, 12)
$off = Get-OutLength
[IO.File]::AppendAllText($in, "$id $Command`n", (New-Object Text.UTF8Encoding($false)))

$deadline = (Get-Date).AddSeconds($TimeoutSec)
$utf8 = New-Object Text.UTF8Encoding($false)
$carry = ''
while ((Get-Date) -lt $deadline) {
    if (Test-Path $out) {
        $fs = [IO.File]::Open($out, 'Open', 'Read', 'ReadWrite')
        try {
            if ($fs.Length -lt $off) { $off = 0; $carry = '' }   # game restarted and truncated the file
            if ($fs.Length -gt $off) {
                $fs.Seek($off, 'Begin') | Out-Null
                $buf = New-Object byte[] ($fs.Length - $off)
                $n = $fs.Read($buf, 0, $buf.Length)
                $off += $n
                $carry += $utf8.GetString($buf, 0, $n)
            }
        } finally { $fs.Dispose() }
        while (($nl = $carry.IndexOf("`n")) -ge 0) {
            $line = $carry.Substring(0, $nl); $carry = $carry.Substring($nl + 1)
            if ($line -notmatch ('"id":"' + $id + '"')) { continue }
            if ($Raw) { $line } else {
                $r = $line | ConvertFrom-Json
                if ($r.ok) {
                    $res = $r.result
                    if ($res -is [psobject] -and $res.PSObject.Properties['rows']) {
                        $res.rows | ForEach-Object { $_ }
                        '--- legend ---'
                        $res.legend | ForEach-Object { ($_ | ConvertTo-Json -Compress -Depth 6) }
                    } else {
                        $res | ConvertTo-Json -Depth 20
                    }
                } else { Write-Error $r.error -ErrorAction Continue }
            }
            if ($line -match '"ok":true') { exit 0 } else { exit 1 }
        }
    }
    Start-Sleep -Milliseconds 100
}
Write-Error "timed out after $TimeoutSec s waiting for the response to '$Command' (id $id)"
exit 3
