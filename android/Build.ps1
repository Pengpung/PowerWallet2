param(
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$Jdk = "$env:USERPROFILE\.jdks\jbr-21.0.11",
    [string]$BuildDirectory = "$env:TEMP\MiGoo-build",
    [string]$ApkPath = "$PSScriptRoot\..\MiGoo-Android.apk"
)
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = $Jdk
$bt = Join-Path $Sdk 'build-tools\36.0.0'
$androidJar = Join-Path $Sdk 'platforms\android-36\android.jar'
if (!(Test-Path -LiteralPath $androidJar)) { $androidJar = Join-Path $Sdk 'platforms\android-37.0\android.jar' }
foreach ($p in @($Jdk,$bt,$androidJar)) { if (!(Test-Path -LiteralPath $p)) { throw "Missing build dependency: $p" } }
New-Item -ItemType Directory -Force -Path $BuildDirectory,"$BuildDirectory\classes","$BuildDirectory\dex","$BuildDirectory\tests" | Out-Null
function CheckExit { if($LASTEXITCODE -ne 0) { throw "Build tool failed with exit code $LASTEXITCODE" } }
$sources = @(Get-ChildItem -LiteralPath "$PSScriptRoot\src" -Recurse -Filter '*.java' | ForEach-Object FullName)
$libs = @(Get-ChildItem -LiteralPath "$PSScriptRoot\libs" -Filter '*.jar' -ErrorAction SilentlyContinue | ForEach-Object FullName)
$classpath = $androidJar
if ($libs.Count -gt 0) { $classpath += ';' + ($libs -join ';') }
& "$Jdk\bin\javac.exe" -encoding UTF-8 -source 8 -target 8 -cp $classpath -d "$BuildDirectory\classes" @sources
CheckExit
& "$Jdk\bin\javac.exe" -encoding UTF-8 -cp "$BuildDirectory\classes" -d "$BuildDirectory\tests" "$PSScriptRoot\test\PressDetectorTest.java"
CheckExit
& "$Jdk\bin\java.exe" -cp "$BuildDirectory\classes;$BuildDirectory\tests" PressDetectorTest
CheckExit
& "$Jdk\bin\jar.exe" cf "$BuildDirectory\classes.jar" -C "$BuildDirectory\classes" .
CheckExit
& "$bt\d8.bat" --lib $androidJar --min-api 28 --output "$BuildDirectory\dex" "$BuildDirectory\classes.jar" @libs
CheckExit
& "$bt\aapt2.exe" compile --dir "$PSScriptRoot\res" -o "$BuildDirectory\res.zip"
CheckExit
$vp=[xml](Get-Content -LiteralPath "$PSScriptRoot\..\version.props" -Raw)
$appVersion=$vp.Project.PropertyGroup.MiGooVersion
$appVersionCode=$vp.Project.PropertyGroup.MiGooVersionCode
& "$bt\aapt2.exe" link -I $androidJar --manifest "$PSScriptRoot\AndroidManifest.xml" --version-code $appVersionCode --version-name $appVersion -o "$BuildDirectory\unsigned.apk" "$BuildDirectory\res.zip"
CheckExit
& "$Jdk\bin\jar.exe" uf "$BuildDirectory\unsigned.apk" -C "$BuildDirectory\dex" classes.dex
CheckExit
& "$bt\zipalign.exe" -f 4 "$BuildDirectory\unsigned.apk" "$BuildDirectory\aligned.apk"
CheckExit
$keyPath = "$BuildDirectory\powerwallet-personal.jks"
if (!(Test-Path -LiteralPath $keyPath)) {
    & "$Jdk\bin\keytool.exe" -genkeypair -keystore $keyPath -alias powerwallet -keyalg RSA -keysize 3072 -validity 10000 -storepass powerwallet-local -keypass powerwallet-local -dname 'CN=PowerWallet Personal Build'
    CheckExit
}
& "$bt\apksigner.bat" sign --ks $keyPath --ks-key-alias powerwallet --ks-pass pass:powerwallet-local --key-pass pass:powerwallet-local --out $ApkPath "$BuildDirectory\aligned.apk"
CheckExit
& "$bt\apksigner.bat" verify --verbose $ApkPath
CheckExit
Write-Output "Built: $ApkPath"
