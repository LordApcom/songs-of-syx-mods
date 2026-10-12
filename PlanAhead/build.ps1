# Compila o mod e monta a pasta pronta para o jogo em dist\<Nome>.
# Uso: .\build.ps1 -Polyphon <caminho\Polyphon.jar>            (só compila)
#      .\build.ps1 -Polyphon <caminho\Polyphon.jar> -Deploy    (compila e copia para %APPDATA%\songsofsyx\mods)
# -Game e -Polyphon também podem vir das variáveis de ambiente SOS_GAME e SOS_POLYPHON.
param(
    [switch]$Deploy,
    # Pasta do jogo, onde fica SongsOfSyx.jar. Padrão: instalação comum da Steam.
    [string]$Game = $(if ($env:SOS_GAME) { $env:SOS_GAME } else { 'C:\Program Files (x86)\Steam\steamapps\common\Songs of Syx' }),
    # Polyphon.jar, dentro da pasta V71\script do mod Polyphon instalado.
    [string]$Polyphon = $env:SOS_POLYPHON
)
$ErrorActionPreference = 'Stop'

if (-not (Test-Path "$Game\SongsOfSyx.jar")) { throw "SongsOfSyx.jar não encontrado em '$Game' (use -Game <pasta do jogo>)" }
if (-not $Polyphon -or -not (Test-Path $Polyphon)) { throw 'Informe o Polyphon.jar: -Polyphon <caminho> ou a variável SOS_POLYPHON' }

$Name  = Split-Path -Leaf $PSScriptRoot
$build = Join-Path $PSScriptRoot 'build'
$dist  = Join-Path $PSScriptRoot "dist\$Name"
Remove-Item -Recurse -Force $build, (Join-Path $PSScriptRoot 'dist') -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$build\classes", "$dist\V71\script" | Out-Null

$sources = Get-ChildItem -Recurse -Filter *.java (Join-Path $PSScriptRoot 'src') | ForEach-Object FullName
javac --release 21 -encoding UTF-8 -nowarn -cp "$Game\SongsOfSyx.jar;$Polyphon" -d "$build\classes" $sources
if ($LASTEXITCODE -ne 0) { throw 'javac falhou' }

Copy-Item -Recurse (Join-Path $PSScriptRoot 'resources\*') "$build\classes"
jar --create --file "$dist\V71\script\$Name.jar" -C "$build\classes" .
if ($LASTEXITCODE -ne 0) { throw 'jar falhou' }

Copy-Item (Join-Path $PSScriptRoot '_Info.txt') $dist
$assets = Join-Path $PSScriptRoot 'assets'
if (Test-Path $assets) { Copy-Item -Recurse $assets "$dist\V71\assets" }
Write-Host "OK: $dist"

if ($Deploy) {
    $target = Join-Path $env:APPDATA "songsofsyx\mods\$Name"
    # falha alto se o jogo estiver aberto (jar travado), para não deixar uma cópia pela metade
    if (Test-Path $target) { Remove-Item -Recurse -Force $target }
    New-Item -ItemType Directory -Force $target | Out-Null
    Copy-Item -Recurse "$dist\*" $target
    Write-Host "Instalado em $target"
}
