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

if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) {
    Write-Error "Gradle is not installed or not available on PATH. Install Gradle, or generate a Gradle wrapper once Gradle is available."
}

if (-not $RunOnly) {
    Write-Host "Building project with Gradle..."
    gradle clean build
}

if (-not $BuildOnly) {
    Write-Host "Running GameEngine..."
    gradle run
}
