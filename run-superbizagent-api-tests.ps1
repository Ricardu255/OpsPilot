param(
    [string]$DashScopeApiKey = $env:DASHSCOPE_API_KEY,
    [string]$QaRepoPath = "D:\code\QA\SuperBizAgent-API-Automation",
    [string]$CondaEnvName = "auto_test",
    [string]$PytestTarget = "testcase/SuperBizAgent",
    [string]$PytestArgs = "-vs --alluredir ./report/temp --clean-alluredir",
    [switch]$RunAiOps = $true,
    [switch]$RunExternal = $true,
    [switch]$Serve,
    [switch]$DryRun
)

# Fill this once if you want to run the script directly without passing -DashScopeApiKey each time.
$StoredDashScopeApiKey = ""

$ErrorActionPreference = "Stop"

function Require-Command {
    param([string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command '$Name' was not found in PATH."
    }
}

function Invoke-Step {
    param(
        [scriptblock]$Action,
        [string]$Description
    )

    Write-Host ""
    Write-Host "==> $Description" -ForegroundColor Cyan
    & $Action
}

if ([string]::IsNullOrWhiteSpace($DashScopeApiKey)) {
    $DashScopeApiKey = $StoredDashScopeApiKey
}

if ([string]::IsNullOrWhiteSpace($DashScopeApiKey) -or $DashScopeApiKey -eq "...") {
    throw "Please provide a real DashScope API key via -DashScopeApiKey or the DASHSCOPE_API_KEY environment variable."
}

if (-not (Test-Path -LiteralPath $QaRepoPath)) {
    throw "QA repository path not found: $QaRepoPath"
}

Require-Command "conda"
Require-Command "allure"

$env:DASHSCOPE_API_KEY = $DashScopeApiKey
$env:SUPERBIZ_RUN_AI_OPS = if ($RunAiOps) { "1" } else { "0" }
$env:SUPERBIZ_RUN_EXTERNAL = if ($RunExternal) { "1" } else { "0" }

$pytestCommand = "python -m pytest $PytestTarget $PytestArgs"
$generateCommand = "allure generate .\report\temp -o .\report\html --clean"
$serveCommand = "allure serve .\report\temp"

Write-Host "QA repo      : $QaRepoPath"
Write-Host "Conda env    : $CondaEnvName"
Write-Host "Pytest target: $PytestTarget"
Write-Host "Run AI Ops   : $($env:SUPERBIZ_RUN_AI_OPS)"
Write-Host "Run External : $($env:SUPERBIZ_RUN_EXTERNAL)"
Write-Host "Serve report : $Serve"

if ($DryRun) {
    Write-Host ""
    Write-Host "Dry run only. Commands that would be executed:" -ForegroundColor Yellow
    Write-Host "cd $QaRepoPath"
    Write-Host "conda run -n $CondaEnvName $pytestCommand"
    Write-Host $generateCommand
    if ($Serve) {
        Write-Host $serveCommand
    }
    exit 0
}

Push-Location $QaRepoPath
try {
    Invoke-Step -Description "Run pytest and collect allure results" -Action {
        conda run -n $CondaEnvName python -m pytest $PytestTarget @($PytestArgs -split ' ')
        if ($LASTEXITCODE -ne 0) {
            throw "Pytest exited with code $LASTEXITCODE"
        }
    }

    Invoke-Step -Description "Generate static Allure HTML report" -Action {
        allure generate .\report\temp -o .\report\html --clean
        if ($LASTEXITCODE -ne 0) {
            throw "Allure generate exited with code $LASTEXITCODE"
        }
    }

    $reportIndex = Join-Path $QaRepoPath "report\html\index.html"
    Write-Host ""
    Write-Host "Static report generated at:" -ForegroundColor Green
    Write-Host $reportIndex

    if ($Serve) {
        Invoke-Step -Description "Serve Allure report" -Action {
            allure serve .\report\temp
        }
    }
}
finally {
    Pop-Location
}
