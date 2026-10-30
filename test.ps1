param(
    [Parameter(Mandatory=$true)][string]$AndroidSdk,
    [Parameter(Mandatory=$true)][string]$JavaBin
)
$ErrorActionPreference = 'Stop'
$android = Join-Path $AndroidSdk 'platforms\android-36\android.jar'
$api = Join-Path $PSScriptRoot 'lib\libxposed-api-102.0.0.jar'
$classes = Join-Path $PSScriptRoot ('test-build\' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $classes | Out-Null
$sources = Get-ChildItem "$PSScriptRoot\src","$PSScriptRoot\tests" -Recurse -Filter '*.java' | ForEach-Object FullName
& "$JavaBin\javac.exe" -encoding UTF-8 --release 17 -cp "$android;$api" -d $classes @sources
if ($LASTEXITCODE) { throw 'Test compilation failed' }
foreach ($test in 'PolicyTest','ProfileTest','BindingTest','HookFlowTest','WarrantyPolicyTest') {
    & "$JavaBin\java.exe" -ea -cp "$classes;$android;$api" "nfc.wallet.compat.$test"
    if ($LASTEXITCODE) { throw "$test failed" }
}

& node "$PSScriptRoot\tests\warranty-script.cjs"
if ($LASTEXITCODE) { throw 'Warranty page fallback checks failed' }
