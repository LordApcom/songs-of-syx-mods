# Builds the mod and assembles the game-ready folder in dist\<Name>.
# Usage: .\build.ps1 -Polyphon <path\Polyphon.jar>            (build only)
#        .\build.ps1 -Polyphon <path\Polyphon.jar> -Deploy    (build and copy to %APPDATA%\songsofsyx\mods)
# -Game and -Polyphon can also come from the SOS_GAME and SOS_POLYPHON environment variables.
param(
    [switch]$Deploy,
    # Game folder, where SongsOfSyx.jar lives. Default: the standard Steam install.
    [string]$Game = $(if ($env:SOS_GAME) { $env:SOS_GAME } else { 'C:\Program Files (x86)\Steam\steamapps\common\Songs of Syx' }),
    # Polyphon.jar, inside the V71\script folder of the installed Polyphon mod.
    [string]$Polyphon = $env:SOS_POLYPHON
)
$ErrorActionPreference = 'Stop'

if (-not (Test-Path "$Game\SongsOfSyx.jar")) { throw "SongsOfSyx.jar not found in '$Game' (use -Game <game folder>)" }
if (-not $Polyphon -or -not (Test-Path $Polyphon)) { throw 'Point to Polyphon.jar: -Polyphon <path> or the SOS_POLYPHON variable' }

$Name  = Split-Path -Leaf $PSScriptRoot
$build = Join-Path $PSScriptRoot 'build'
$dist  = Join-Path $PSScriptRoot "dist\$Name"
Remove-Item -Recurse -Force $build, (Join-Path $PSScriptRoot 'dist') -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$build\classes", "$dist\V71\script" | Out-Null

$sources = Get-ChildItem -Recurse -Filter *.java (Join-Path $PSScriptRoot 'src') | ForEach-Object FullName
javac --release 21 -encoding UTF-8 -nowarn -cp "$Game\SongsOfSyx.jar;$Polyphon" -d "$build\classes" $sources
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

Copy-Item -Recurse (Join-Path $PSScriptRoot 'resources\*') "$build\classes"
jar --create --file "$dist\V71\script\$Name.jar" -C "$build\classes" .
if ($LASTEXITCODE -ne 0) { throw 'jar failed' }

Copy-Item (Join-Path $PSScriptRoot '_Info.txt') $dist
$assets = Join-Path $PSScriptRoot 'assets'
if (Test-Path $assets) { Copy-Item -Recurse $assets "$dist\V71\assets" }
Write-Host "OK: $dist"

if ($Deploy) {
    $target = Join-Path $env:APPDATA "songsofsyx\mods\$Name"
    # fails loudly if the game is running (jar locked), so a half-copied mod is never left behind
    if (Test-Path $target) { Remove-Item -Recurse -Force $target }
    New-Item -ItemType Directory -Force $target | Out-Null
    Copy-Item -Recurse "$dist\*" $target
    Write-Host "Installed to $target"
}
