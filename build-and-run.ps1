param(
    [switch]$BuildOnly,
    [switch]$RunOnly
)

$ErrorActionPreference = "Stop"

if ($BuildOnly -and $RunOnly) {
    Write-Error "Use either -BuildOnly or -RunOnly, not both."
}

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projectRoot

$gradlew = Join-Path $projectRoot "gradlew.bat"
if (-not (Test-Path $gradlew)) {
    Write-Error "Gradle wrapper not found at $gradlew"
}

if (-not $RunOnly) {
    Write-Host "Building project with Gradle..."
    & $gradlew clean compileJava
}

if (-not $BuildOnly) {
    Write-Host "Running GameEngine..."
    & $gradlew run
}
