# Create an orphan branch named 'stable' using this folder's content as the initial commit.
# Run from the repo root (where .git lives). If this folder is not yet a clone,
# clone first: git clone https://github.com/chibashr/ReplayGIF.git <folder>; cd <folder>
# Author: chibashr

$ErrorActionPreference = "Stop"
$gitExe = (Get-Command git -ErrorAction SilentlyContinue).Source
if (-not $gitExe) {
    $gitExe = "${env:ProgramFiles}\Git\bin\git.exe"
    if (-not (Test-Path $gitExe)) { $gitExe = "${env:ProgramFiles(x86)}\Git\bin\git.exe" }
    if (-not (Test-Path $gitExe)) { Write-Error "Git not found. Install Git or add it to PATH."; exit 1 }
}
function git { & $gitExe @args }

$RepoRoot = if ($PSScriptRoot) { Split-Path $PSScriptRoot -Parent } else { Get-Location }
Set-Location $RepoRoot

# Initialize repo and add remote if not already a git repo
if (-not (Test-Path ".git")) {
    git init
    git remote add origin "https://github.com/chibashr/ReplayGIF.git"
    Write-Host "Initialized git repo and added origin."
}

$OrphanBranchName = "stable"

# Create orphan branch (no parent commits)
git checkout --orphan $OrphanBranchName

# Stage current folder content as the initial commit for stable
git add .

Write-Host "Orphan branch '$OrphanBranchName' created. Current folder content is staged."
Write-Host "Next steps:"
Write-Host "  1. Commit: git commit -m 'Stable initial commit'"
Write-Host "  2. Push: git push -u origin $OrphanBranchName"
Write-Host "  3. On GitHub: Settings -> Default branch -> set to '$OrphanBranchName', then delete 'master' if desired."
