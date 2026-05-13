<#
.SYNOPSIS
  Renames sprite files by removing the leading "Modern-" prefix from each filename.

.PARAMETER Directory
  Folder containing the images. Defaults to src/main/resources/assets/units under the repo root.

.PARAMETER WhatIf
  If set, only prints what would be renamed; no files are changed.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/remove-modern-prefix-from-sprites.ps1 -WhatIf

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/remove-modern-prefix-from-sprites.ps1
#>
param(
    [string] $Directory = "",
    [switch] $WhatIf
)

$ErrorActionPreference = "Stop"
$prefix = "Modern-"

if ([string]::IsNullOrWhiteSpace($Directory)) {
    $Directory = "C:\Users\Thijssenj\Projects\BattalionRevival\battalion-browser\public\assets\terrain\animated"
}
$Directory = [System.IO.Path]::GetFullPath($Directory)

if (-not (Test-Path -LiteralPath $Directory -PathType Container)) {
    throw "Directory not found: $Directory"
}

Get-ChildItem -LiteralPath $Directory -File | Where-Object { $_.Name.StartsWith($prefix) } | ForEach-Object {
    $oldName = $_.Name
    $newName = $oldName.Substring($prefix.Length)
    if ([string]::IsNullOrWhiteSpace($newName)) {
        Write-Warning "Skip (empty target name): $oldName"
        return
    }
    $dest = Join-Path $_.DirectoryName $newName
    if (Test-Path -LiteralPath $dest) {
        Write-Warning "Skip (target already exists): $oldName -> $newName"
        return
    }
    if ($WhatIf) {
        Write-Host "Would rename: $oldName -> $newName"
        return
    }
    Rename-Item -LiteralPath $_.FullName -NewName $newName
    Write-Host "Renamed: $oldName -> $newName"
}

Write-Host "Done. Folder: $Directory"
