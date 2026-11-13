param([string]$OutputDirectory = "$PSScriptRoot\..\dist")
$ErrorActionPreference = 'Stop'
$compiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
$framework = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319'
if(!(Test-Path -LiteralPath $compiler)){throw '.NET Framework 4.8 的 C# 编译器不可用。'}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$vp=[xml](Get-Content -LiteralPath "$PSScriptRoot\..\version.props" -Raw)
$version=$vp.Project.PropertyGroup.MiGooVersion
$apkName=$vp.Project.PropertyGroup.MiGooApkName
$exeName=$vp.Project.PropertyGroup.MiGooExeName
$generated="$PSScriptRoot\GeneratedVersion.cs"
Set-Content -LiteralPath $generated -Encoding UTF8 -Value @"
namespace PowerWallet {
    public static class BuildInfo {
        public const string Version = "$version";
        public const string ApkName = "$apkName";
        public const string ExeName = "$exeName";
    }
}
"@
$output = Join-Path (Resolve-Path $OutputDirectory) $exeName
$arguments = @('/nologo','/target:winexe','/platform:x64','/optimize+','/utf8output','/codepage:65001',"/out:$output","/win32manifest:$PSScriptRoot\app.manifest","/win32icon:$PSScriptRoot\assets\PowerWallet.ico",'/r:System.dll','/r:System.Core.dll','/r:System.Xaml.dll',"/r:$framework\WPF\WindowsBase.dll","/r:$framework\WPF\PresentationCore.dll","/r:$framework\WPF\PresentationFramework.dll")
foreach($asset in @('adb.exe','AdbWinApi.dll','AdbWinUsbApi.dll',$apkName,'NOTICE.txt','source.properties')) {
    $assetPath=Join-Path "$PSScriptRoot\assets" $asset
    if(!(Test-Path -LiteralPath $assetPath)){throw "缺少打包资源：$asset"}
    $arguments += "/resource:$assetPath,PowerWallet.Assets.$asset"
}
$arguments += $generated
$arguments += @(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.cs' | Where-Object { $_.Name -ne 'GeneratedVersion.cs' } | ForEach-Object FullName)
& $compiler @arguments
if($LASTEXITCODE -ne 0){throw 'EXE 编译失败。'}
Write-Output "Built: $output"
