$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$frontend = Join-Path $root 'frontend'
$backend = Join-Path $root 'backend'
$static = Join-Path $backend 'src\main\resources\static'
$launcher = Join-Path $root 'launcher\SgsLanLauncher.cs'
$exe = Join-Path $root 'SGS-LAN.exe'

Push-Location $frontend
try {
    npm ci
    npm run build
}
finally {
    Pop-Location
}

if (Test-Path -LiteralPath $static) {
    Remove-Item -LiteralPath $static -Recurse -Force
}
New-Item -ItemType Directory -Path $static | Out-Null
Copy-Item -Path (Join-Path $frontend 'dist\*') -Destination $static -Recurse -Force

Push-Location $backend
try {
    mvn package -DskipTests
}
finally {
    Pop-Location
}

$csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (!(Test-Path -LiteralPath $csc)) {
    $csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework\v4.0.30319\csc.exe'
}
if (!(Test-Path -LiteralPath $csc)) {
    throw 'Cannot find csc.exe. Install .NET Framework developer tools or build the launcher manually.'
}

& $csc /nologo /codepage:65001 /target:winexe /out:$exe /reference:System.dll /reference:System.Drawing.dll /reference:System.Windows.Forms.dll $launcher

Write-Host 'Build complete.'
Write-Host "Launcher: $exe"
Write-Host "Backend jar: $(Join-Path $backend 'target\assistant-0.0.1-SNAPSHOT.jar')"
