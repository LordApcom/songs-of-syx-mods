# Closes the game, reinstalls the requested mods, reopens it and loads a save until the settlement is running.
# Usage: .\reload.ps1 -Save landtest [-Mods SosTestBridge,PlanAhead]
# Note: each mod's build.ps1 is called with -Deploy and needs -Polyphon or the SOS_POLYPHON env var, so SOS_POLYPHON must be set.
param([Parameter(Mandatory = $true)][string]$Save, [string[]]$Mods = @())
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$bridge = Join-Path $PSScriptRoot 'bridge.ps1'
$Status = Join-Path $env:APPDATA 'songsofsyx\bridge\status.json'

& "$PSScriptRoot\start-game.ps1" -Kill | Out-Null
Start-Sleep 3
foreach ($m in $Mods) { & "$root\$m\build.ps1" -Deploy | Select-Object -Last 1 }
& "$PSScriptRoot\start-game.ps1" -TimeoutSec 240
& $bridge "load $Save" -TimeoutSec 20 | Out-Null
$deadline = (Get-Date).AddSeconds(240)
while ((Get-Date) -lt $deadline) {
    $s = $null
    try { $s = Get-Content $Status -Raw | ConvertFrom-Json } catch {}
    if ($s -and $s.screen -eq 'settlement' -and $s.settlement) {
        & $bridge 'dismiss' | Out-Null   # "click to continue" prompt after loading
        Write-Host "loaded: $Save"
        exit 0
    }
    Start-Sleep -Milliseconds 500
}
throw "save '$Save' did not load"
