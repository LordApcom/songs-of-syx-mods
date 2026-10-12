# Creates a new test game through the bridge, from the menu until the settlement is running.
# Usage: .\new-test-game.ps1 [-Race <key>] [-Seed <n>] [-Capital "cx cy"|auto] [-Landing "x y"|auto] [-Save <name>]
param([string]$Race = '', [int]$Seed = 0, [string]$Capital = 'auto', [string]$Landing = 'auto', [string]$Save = '')
$ErrorActionPreference = 'Stop'
$b = Join-Path $PSScriptRoot 'bridge.ps1'
$Status = Join-Path $env:APPDATA 'songsofsyx\bridge\status.json'

function Step([string]$cmd, [int]$t = 120) {
    Write-Host "> $cmd"
    & $b $cmd -TimeoutSec $t
    if ($LASTEXITCODE -ne 0) { throw "failed: $cmd" }
}
function WaitScreen([string]$want, [int]$t = 300) {
    $deadline = (Get-Date).AddSeconds($t)
    while ((Get-Date) -lt $deadline) {
        try { $s = Get-Content $Status -Raw | ConvertFrom-Json; if ($s.screen -eq $want) { return } } catch {}
        Start-Sleep -Milliseconds 500
    }
    throw "screen '$want' did not appear within $t s"
}

WaitScreen 'menu'
Step 'newgame'
WaitScreen 'worldgen'
Step ("wg race $Race").Trim()
if ($Seed -ne 0) { Step "wg terrain $Seed" 600 } else { Step 'wg terrain' 600 }
Step "wg capital $Capital" 600
Step 'wg finish' 900
WaitScreen 'landing' 900
Step "landing $Landing"
WaitScreen 'settlement' 120
Step 'status'
if ($Save) { Step "save $Save" 300 }
