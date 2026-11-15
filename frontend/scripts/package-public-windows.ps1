param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-zA-Z0-9][a-zA-Z0-9.-]*$')]
    [string]$ServerHost,
    [ValidateRange(1, 65535)][int]$ServerPort = 2209
)

$ErrorActionPreference = 'Stop'
$frontend = Split-Path $PSScriptRoot -Parent
$libraries = Join-Path $frontend 'build\install\frontend\lib'
$runtime = Join-Path $frontend 'build\game-runtime'
$mainJar = Get-ChildItem -LiteralPath $libraries -Filter 'frontend-*.jar' -File
if (@($mainJar).Count -ne 1 -or !(Test-Path (Join-Path $runtime 'bin\java.exe'))) {
    throw 'Run :frontend:installDist :frontend:createGameRuntime before packaging.'
}
$jpackage = (Get-Command jpackage.exe -CommandType Application).Source
$jdk = Split-Path (Split-Path $jpackage -Parent) -Parent
$release = Join-Path $frontend 'build\public-release'
New-Item -ItemType Directory -Force -Path $release | Out-Null
$stage = Join-Path $release ([guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $stage | Out-Null

try {
    $javaOptions = "--enable-native-access=ALL-UNNAMED -Dbattleship.tls=true -Dbattleship.host=$ServerHost -Dbattleship.port=$ServerPort"
    & $jpackage --type app-image --dest $stage --name Battleship --input $libraries `
        --main-jar $mainJar.Name --main-class com.battleship.frontend.app.BattleshipApplication `
        --runtime-image $runtime --java-options $javaOptions
    if ($LASTEXITCODE -ne 0) { throw 'jpackage could not create the Windows application.' }

    $app = Join-Path $stage 'Battleship'
    $javaLicense = Join-Path $jdk 'LICENSE'
    if (Test-Path -LiteralPath $javaLicense) {
        Copy-Item -LiteralPath $javaLicense -Destination (Join-Path $app 'JAVA-LICENSE.txt')
    }
    @"
BATTLESHIP 3D - WINDOWS X64

Extract the whole ZIP and open Battleship.exe. Keep the app and runtime folders beside it.
Java is included. The game automatically connects to ${ServerHost}:${ServerPort} over TLS.
Use CREATE AN ACCOUNT to register or SIGN IN with your Battleship account.
"@ | Set-Content -LiteralPath (Join-Path $app 'START-HERE.txt') -Encoding UTF8

    $zipName = 'Battleship-Windows-x64.zip'
    $zip = Join-Path $stage $zipName
    tar.exe -a -cf $zip -C $stage Battleship
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the Windows ZIP.' }

    $artifact = Join-Path $release $zipName
    Move-Item -LiteralPath $zip -Destination $artifact -Force
    Get-Item -LiteralPath $artifact | Select-Object FullName, Length
} finally {
    $resolvedRelease = (Resolve-Path -LiteralPath $release).Path
    $resolvedStage = (Resolve-Path -LiteralPath $stage).Path
    if ((Split-Path $resolvedStage -Parent) -eq $resolvedRelease) {
        Remove-Item -LiteralPath $resolvedStage -Recurse -Force
    }
}
