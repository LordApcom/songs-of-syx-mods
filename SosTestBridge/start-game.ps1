# Starts Songs of Syx without the launcher (same path as the Play button: init.Main without the "launcher" argument,
# which reads mods and JVM arguments from LauncherSettings.txt) and waits for the bridge to answer at the menu.
# Usage: .\start-game.ps1            starts minimized (without stealing focus) and waits
#        .\start-game.ps1 -Show      starts with the window on screen
#        .\start-game.ps1 -Kill      closes the running game (only java processes running SongsOfSyx.jar, by PID)
param(
    [switch]$Kill, [switch]$Show, [int]$TimeoutSec = 180,
    [string]$Game = $(if ($env:SOS_GAME) { $env:SOS_GAME } else { 'C:\Program Files (x86)\Steam\steamapps\common\Songs of Syx' })
)
$ErrorActionPreference = 'Stop'

$Status = Join-Path $env:APPDATA 'songsofsyx\bridge\status.json'

function Get-GamePids {
    Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" |
        Where-Object { $_.CommandLine -match 'SongsOfSyx\.jar' -or $_.CommandLine -match 'init\.MainProcess' } |
        ForEach-Object { $_.ProcessId }
}

if ($Kill) {
    foreach ($id in Get-GamePids) { Stop-Process -Id $id -Force; Write-Host "closed PID $id" }
    exit 0
}

if (Get-GamePids) { throw 'The game is already running. Use -Kill first if you want to reopen it.' }

Remove-Item $Status -ErrorAction SilentlyContinue
# The bridge stays off unless this opt-in file exists.
New-Item -ItemType Directory -Force (Join-Path $env:APPDATA 'songsofsyx\bridge') | Out-Null
New-Item -ItemType File -Force (Join-Path $env:APPDATA 'songsofsyx\bridge\ENABLED') | Out-Null
Start-Process -FilePath "$Game\jre\bin\java.exe" -ArgumentList '-cp', 'SongsOfSyx.jar', 'init.Main' `
    -WorkingDirectory $Game -WindowStyle Hidden | Out-Null

Add-Type -Namespace W -Name U -MemberDefinition @'
[DllImport("user32.dll")] public static extern bool ShowWindow(System.IntPtr h, int c);
[DllImport("user32.dll")] public static extern bool IsIconic(System.IntPtr h);
'@
# init.Main opens a second java process (init.MainProcess) and the window is recreated while loading,
# so keep minimizing until the bridge answers. 7 = minimize without activating.
function Hide-Game {
    if ($Show) { return }
    foreach ($id in Get-GamePids) {
        $h = (Get-Process -Id $id -ErrorAction SilentlyContinue).MainWindowHandle
        if ($h -and $h -ne [IntPtr]::Zero -and -not [W.U]::IsIconic($h)) { [W.U]::ShowWindow($h, 7) | Out-Null }
    }
}
$deadline = (Get-Date).AddSeconds($TimeoutSec)
while ((Get-Date) -lt $deadline) {
    Hide-Game
    if (Test-Path $Status) {
        $s = Get-Content $Status -Raw | ConvertFrom-Json
        if ($s.alive) { Start-Sleep 2; Hide-Game; Write-Host "ready (screen: $($s.screen))"; exit 0 }
    }
    Start-Sleep -Milliseconds 500
}
Write-Error "the bridge did not respond within $TimeoutSec s"
exit 3
