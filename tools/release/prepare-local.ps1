param(
    [string]$Python = 'python3',
    [string]$RuntimeVerifierPython = 'python3',
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$releaseRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$releaseProfile = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'release-profile.json') -Raw | ConvertFrom-Json
if ($releaseProfile.publishAllowed -ne $false) { throw 'This tool only prepares a local held candidate.' }
if (!(Test-Path -LiteralPath (Join-Path $releaseRoot 'keystore.properties'))) {
    throw 'Configure the existing private signing key locally; do not generate or publish a replacement key.'
}
Push-Location $releaseRoot
try {
    & $Python tools/release/fetch-voice-runtime.py --verify-only
    if ($LASTEXITCODE -ne 0) { throw 'Voice runtime hash verification failed.' }
    $releaseArgs = @(':app:writeReleaseInventory', ':app:testDebugUnitTest')
    foreach ($suite in $releaseProfile.selectedTests) { $releaseArgs += @('--tests', $suite) }
    $releaseArgs += @(':app:assembleRelease', '-I', 'tools/release/dependency-inventory.init.gradle',
        '-I', 'tools/release/voice-unit-tests.init.gradle', '-PcyreneBuild=true',
        "-PversionName=$($releaseProfile.versionName)", "-PversionCode=$($releaseProfile.versionCode)",
        "-PcyreneRuntimePython=$RuntimeVerifierPython",
        '-Dorg.gradle.jvmargs=-Xmx6g -Dfile.encoding=UTF-8 -XX:+UseParallelGC',
        '-Dorg.gradle.parallel=false', '--max-workers=1', '--no-configuration-cache', '--no-daemon')
    if ($Offline) { $releaseArgs += '--offline' }
    & .\gradlew.bat @releaseArgs
    if ($LASTEXITCODE -ne 0) { throw 'Build or selected tests failed; do not use partial outputs.' }
    Write-Output 'Local candidate prepared. Publication remains on hold. No upload or installation was performed.'
} finally { Pop-Location }
