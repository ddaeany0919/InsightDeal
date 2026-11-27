# InsightDeal (얼만데) 노트북 한국 IP 보조 워커 원클릭 설치 스크립트 (Windows PowerShell)
param(
    [string]$CloudApiUrl = "https://34.59.208.14.sslip.io/api/community/ingest-batch",
    [int]$IntervalSec = 180
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $RepoRoot

Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host " InsightDeal 노트북(한국 IP) 보조 워커 셋업" -ForegroundColor Cyan
Write-Host "=====================================================" -ForegroundColor Cyan

# 1. Python 확인
$pyCmd = Get-Command python -ErrorAction SilentlyContinue
if (-not $pyCmd) {
    $pyCmd = Get-Command py -ErrorAction SilentlyContinue
}
if (-not $pyCmd) {
    Write-Host "[오류] Python 3.10+가 설치되어 있지 않습니다. python.org 또는 Microsoft Store에서 Python 설치 후 다시 실행하세요." -ForegroundColor Red
    exit 1
}

# 2. 전용 가상환경(.venv-laptop) 생성 및 필수 패키지 설치
$VenvDir = Join-Path $RepoRoot ".venv-laptop"
if (-not (Test-Path (Join-Path $VenvDir "Scripts\python.exe"))) {
    Write-Host "[1/3] 경량 Python 가상환경(.venv-laptop) 생성 중..." -ForegroundColor Yellow
    & $pyCmd.Source -m venv $VenvDir
}
$VenvPy = Join-Path $VenvDir "Scripts\python.exe"
$VenvPip = Join-Path $VenvDir "Scripts\pip.exe"

Write-Host "[2/3] 필수 크롤링/네트워크 패키지 설치 중..." -ForegroundColor Yellow
& $VenvPip install --upgrade pip --quiet
& $VenvPip install httpx beautifulsoup4 python-dotenv curl_cffi lxml sqlalchemy pydantic --quiet

# 3. .env.laptop 생성 (FEEDER_API_TOKEN 입력 또는 기존 .env에서 복사)
$EnvLaptop = Join-Path $RepoRoot ".env.laptop"
if (-not (Test-Path $EnvLaptop)) {
    $tokenVal = ""
    $rootEnv = Join-Path $RepoRoot ".env"
    if (Test-Path $rootEnv) {
        $matchLine = Get-Content $rootEnv -Encoding UTF8 | Where-Object { $_ -match '^\s*FEEDER_API_TOKEN\s*=' } | Select-Object -First 1
        if ($matchLine) {
            $tokenVal = ($matchLine -split '=', 2)[1].Trim().Trim('"').Trim("'")
        }
    }
    if (-not $tokenVal) {
        $tokenVal = Read-Host "GCP FEEDER_API_TOKEN 값을 붙여넣으세요 (없으면 Enter로 나중에 직접 입력)"
    }
    $lines = @(
        "IS_CLOUD=false",
        "CLOUD_API_URL=$CloudApiUrl",
        "CLOUD_DEALS_API_URL=https://34.59.208.14.sslip.io/api/community/hot-deals",
        "FEEDER_API_TOKEN=$tokenVal",
        "LAPTOP_INTERVAL_SEC=$IntervalSec"
    )
    [System.IO.File]::WriteAllLines($EnvLaptop, $lines, [System.Text.UTF8Encoding]::new($false))
    Write-Host "[완료] .env.laptop 생성 완료 (Git 추적 제외됨)" -ForegroundColor Green
} else {
    Write-Host "[확인] 기존 .env.laptop 유지: $EnvLaptop" -ForegroundColor Green
}

# 4. Windows 시작프로그램 자동 등록 여부 선택
$startupAns = Read-Host "[3/3] 윈도우 부팅 시 자동 실행되도록 시작프로그램에 등록할까요? (Y/N)"
if ($startupAns -match '^[Yy]') {
    $StartupFolder = [Environment]::GetFolderPath("Startup")
    $ShortcutPath = Join-Path $StartupFolder "InsightDeal-KR-Worker.lnk"
    $BatPath = Join-Path $RepoRoot "scripts\start_laptop_worker.bat"
    $WshShell = New-Object -ComObject WScript.Shell
    $Shortcut = $WshShell.CreateShortcut($ShortcutPath)
    $Shortcut.TargetPath = $BatPath
    $Shortcut.WorkingDirectory = $RepoRoot
    $Shortcut.WindowStyle = 7
    $Shortcut.Save()
    Write-Host "[완료] 시작프로그램 등록됨: $ShortcutPath" -ForegroundColor Green
}

Write-Host ""
Write-Host "실행 방법: scripts\start_laptop_worker.bat 더블클릭" -ForegroundColor Cyan
$runNow = Read-Host "지금 바로 한국 IP 워커를 실행할까요? (Y/N)"
if ($runNow -match '^[Yy]') {
    & $VenvPy (Join-Path $RepoRoot "backend\scripts\laptop_kr_worker.py")
}
