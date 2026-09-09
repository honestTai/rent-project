$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$deploy = Join-Path $root 'customer-deploy'
$frontendRoot = Join-Path $root 'equipment_management_system_fornt'

function Resolve-Maven {
    $command = Get-Command 'mvn.cmd' -ErrorAction SilentlyContinue
    if (-not $command) { $command = Get-Command 'mvn' -ErrorAction SilentlyContinue }
    if (-not $command) { throw 'Install Maven 3.6+ and add its bin directory to PATH.' }
    return $command.Source
}

function Copy-Jar($module, $targetName) {
    $jar = Join-Path $root "$module\target\$module-0.0.1-SNAPSHOT.jar"
    if (-not (Test-Path $jar)) {
        throw "Missing jar: $jar"
    }
    Copy-Item $jar (Join-Path $deploy "jars\$targetName") -Force
}

function Build-Frontend($workspace, $baseUrl, $targetDir, $platformOrigin = '') {
    Push-Location $frontendRoot
    try {
        $env:VITE_BASE_URL = $baseUrl
        $env:VITE_API_URL_PREFIX = '/api'
        $env:VITE_GATEWAY_ORIGIN = '/gateway'
        $env:VITE_PLATFORM_ORIGIN = $platformOrigin
        $env:VITE_PLATFORM_PUBLIC_PATH = '/platform/'
        npm --workspace $workspace run build
        if ($LASTEXITCODE -ne 0) { throw "Frontend build failed: $workspace" }
    } finally {
        Remove-Item Env:\VITE_BASE_URL -ErrorAction SilentlyContinue
        Remove-Item Env:\VITE_API_URL_PREFIX -ErrorAction SilentlyContinue
        Remove-Item Env:\VITE_GATEWAY_ORIGIN -ErrorAction SilentlyContinue
        Remove-Item Env:\VITE_PLATFORM_ORIGIN -ErrorAction SilentlyContinue
        Remove-Item Env:\VITE_PLATFORM_PUBLIC_PATH -ErrorAction SilentlyContinue
        Pop-Location
    }

    $source = Join-Path $frontendRoot "apps\$($workspace.Replace('@ems/', '').Replace('-tdesign', '-tdesign'))\dist"
    if (-not (Test-Path $source)) {
        throw "Missing frontend dist: $source"
    }
    if (Test-Path $targetDir) {
        $allowedRoot = [IO.Path]::GetFullPath((Join-Path $deploy 'www')) + [IO.Path]::DirectorySeparatorChar
        $resolvedTarget = [IO.Path]::GetFullPath($targetDir)
        if (-not $resolvedTarget.StartsWith($allowedRoot, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Refusing to replace a directory outside customer-deploy/www.'
        }
        Get-ChildItem -LiteralPath $resolvedTarget -Force | ForEach-Object { Remove-Item -LiteralPath $_.FullName -Recurse -Force }
    } else {
        New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
    }
    Copy-Item (Join-Path $source '*') $targetDir -Recurse -Force
}

New-Item -ItemType Directory -Force -Path (Join-Path $deploy 'jars') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $deploy 'www\platform') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $deploy 'www\rent') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $deploy 'logs') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $deploy 'data\mysql') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $deploy 'data\redis') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $deploy 'uploads') | Out-Null

Push-Location $root
try {
    $mvn = Resolve-Maven
    & $mvn -pl equipment-eureka,equipment-platform,equipment-alipay,equipment-gateway -am -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'Backend packaging failed.' }
    Copy-Jar 'equipment-eureka' 'equipment-eureka.jar'
    Copy-Jar 'equipment-platform' 'equipment-platform.jar'
    Copy-Jar 'equipment-alipay' 'equipment-alipay.jar'
    Copy-Jar 'equipment-gateway' 'equipment-gateway.jar'
} finally {
    Pop-Location
}

Build-Frontend '@ems/platform-tdesign' '/platform/' (Join-Path $deploy 'www\platform')
Build-Frontend '@ems/rent-tdesign' '/rent/' (Join-Path $deploy 'www\rent')

Write-Host "Customer deploy package is ready: $deploy"

