param(
    [Parameter(Mandatory = $true)][string]$RuntimeDirectory,
    [Parameter(Mandatory = $true)][string]$LibraryDirectory,
    [ValidateSet('Auto', 'Integrated')][string]$GpuPreference = 'Auto',
    [string]$ServerHost = 'localhost',
    [int]$ServerPort = 2209,
    [string]$PlayerName = 'Captain'
)

$ErrorActionPreference = 'Stop'
$gameJava = Join-Path (Resolve-Path -LiteralPath $RuntimeDirectory).Path 'bin\java.exe'
$gameLibraries = (Resolve-Path -LiteralPath $LibraryDirectory).Path
$preferenceKey = 'HKCU:\Software\Microsoft\DirectX\UserGpuPreferences'
if (-not (Test-Path -LiteralPath $preferenceKey)) {
    New-Item -Path $preferenceKey -Force | Out-Null
}

# Only this game's private runtime gets the preference; system Java and GPU devices are untouched.
$entry = (Get-ItemProperty -LiteralPath $preferenceKey).PSObject.Properties[$gameJava]
$previous = if ($entry) { [string]$entry.Value } else { '' }
function Set-GameGpuPreference([int]$value) {
    $setting = "GpuPreference=$value;"
    $preference = if ($previous -match 'GpuPreference=\d+;') {
        $previous -replace 'GpuPreference=\d+;', $setting
    } else {
        $setting + $previous
    }
    New-ItemProperty -LiteralPath $preferenceKey -Name $gameJava -PropertyType String -Value $preference -Force | Out-Null
}

function Start-Game {
    & $gameJava '--enable-native-access=ALL-UNNAMED' "-Dbattleship.host=$ServerHost" `
        "-Dbattleship.port=$ServerPort" "-Dbattleship.player=$PlayerName" '-cp' (Join-Path $gameLibraries '*') `
        'com.battleship.frontend.app.BattleshipApplication' | Out-Host
    return $LASTEXITCODE
}

# High performance selects the fastest available GPU, including integrated-only machines.
Set-GameGpuPreference $(if ($GpuPreference -eq 'Integrated') { 1 } else { 2 })
Write-Host "GPU preference: $GpuPreference for $gameJava"
$gameExitCode = Start-Game
if ($GpuPreference -eq 'Auto' -and $gameExitCode -eq 2) {
    Write-Host 'OpenGL startup failed; retrying once with the integrated GPU preference.'
    Set-GameGpuPreference 1
    $gameExitCode = Start-Game
    # Next launch should prefer the fastest available GPU again.
    Set-GameGpuPreference 2
}
exit $gameExitCode
