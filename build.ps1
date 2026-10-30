param(
    [Parameter(Mandatory=$true)][string]$AndroidSdk,
    [Parameter(Mandatory=$true)][string]$JavaBin,
    [Parameter(Mandatory=$true,ParameterSetName='Signed')][string]$SigningKey,
    [Parameter(Mandatory=$true,ParameterSetName='Signed')][string]$PasswordFile,
    [Parameter(Mandatory=$true,ParameterSetName='Unsigned')][switch]$Unsigned,
    [string]$KeyAlias = 'wallet-local',
    [string]$OutputApk = (Join-Path $PSScriptRoot 'nfc-wallet-compat-0.6.1.apk')
)
$ErrorActionPreference = 'Stop'
$tools = Join-Path $AndroidSdk 'build-tools\36.0.0'
$android = Join-Path $AndroidSdk 'platforms\android-36\android.jar'
$api = Join-Path $PSScriptRoot 'lib\libxposed-api-102.0.0.jar'
if ((Get-FileHash $api -Algorithm SHA256).Hash -ine (Get-Content "$PSScriptRoot\lib\api.sha256" -Raw).Trim()) { throw 'API dependency changed' }
$build = Join-Path $PSScriptRoot ('build\' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path "$build\classes","$build\dex" -Force | Out-Null
$sources = Get-ChildItem (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java' | ForEach-Object FullName
& "$JavaBin\javac.exe" -encoding UTF-8 --release 17 -cp "$android;$api" -d "$build\classes" @sources
if ($LASTEXITCODE) { throw 'javac failed' }
& "$JavaBin\jar.exe" cf "$build\classes.jar" -C "$build\classes" .
if ($LASTEXITCODE) { throw 'jar failed' }
& "$JavaBin\java.exe" -cp "$tools\lib\d8.jar" com.android.tools.r8.D8 --min-api 28 --lib $android --classpath $api --output "$build\dex" "$build\classes.jar"
if ($LASTEXITCODE) { throw 'D8 failed' }
& "$tools\aapt2.exe" compile --dir "$PSScriptRoot\res" -o "$build\resources.zip"
if ($LASTEXITCODE) { throw 'aapt2 compile failed' }
& "$tools\aapt2.exe" link -o "$build\unsigned.apk" --manifest "$PSScriptRoot\AndroidManifest.xml" -I $android "$build\resources.zip"
if ($LASTEXITCODE) { throw 'aapt2 link failed' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::Open("$build\unsigned.apk", [IO.Compression.ZipArchiveMode]::Update)
try {
    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, "$build\dex\classes.dex", 'classes.dex') | Out-Null
    foreach ($name in 'java_init.list','scope.list','module.prop') {
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, "$PSScriptRoot\META-INF\xposed\$name", "META-INF/xposed/$name") | Out-Null
    }
} finally { $archive.Dispose() }
& "$tools\zipalign.exe" -f -p 4 "$build\unsigned.apk" "$build\aligned.apk"
if ($LASTEXITCODE) { throw 'zipalign failed' }
if ($Unsigned) {
    Copy-Item -LiteralPath "$build\aligned.apk" -Destination $OutputApk
    Write-Output "Unsigned build ready: $OutputApk"
    exit 0
}
& "$JavaBin\java.exe" -jar "$tools\lib\apksigner.jar" sign --ks $SigningKey --ks-key-alias $KeyAlias --ks-pass "file:$PasswordFile" --out $OutputApk "$build\aligned.apk"
if ($LASTEXITCODE) { throw 'signing failed' }
& "$JavaBin\java.exe" -jar "$tools\lib\apksigner.jar" verify --verbose $OutputApk
if ($LASTEXITCODE) { throw 'signature verification failed' }
