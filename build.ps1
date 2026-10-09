# MITE-扎根 (MITE-Rooted) —— 一键编译打包
# 用法： powershell -ExecutionPolicy Bypass -File build.ps1
#        可选参数（不改就用下面默认值）：
#          -McDir   <游戏 .minecraft 目录>
#          -JdkBin  <JDK 17 的 bin 目录>
#
# 依赖（都在你自己的 MITE 客户端里，不随本仓库分发）：
#   .fml\remappedJars\1.6.4-MITE.jar-3.4.2.jar   MITE 的映射版 jar（编译用）
#   libraries\net\xiaoyu233\fishmodloader\fishmodloader\v3.4.2\FishModLoader-v3.4.2.jar
#   libraries\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar
#   libraries\org\lwjgl\lwjgl\lwjgl\2.9.4-nightly-20150209\lwjgl-2.9.4-nightly-20150209.jar
param(
    [string]$McDir  = 'D:\AI\MITE\MITE R196原始无模组端\.minecraft',
    [string]$JdkBin = 'D:\java17\bin'
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$mc   = $McDir
$jdk  = $JdkBin

$remap = Join-Path $mc '.fml\remappedJars\1.6.4-MITE.jar-3.4.2.jar'
$fml   = Join-Path $mc 'libraries\net\xiaoyu233\fishmodloader\fishmodloader\v3.4.2\FishModLoader-v3.4.2.jar'
$gson  = Join-Path $mc 'libraries\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar'
$lwjgl = Join-Path $mc 'libraries\org\lwjgl\lwjgl\lwjgl\2.9.4-nightly-20150209\lwjgl-2.9.4-nightly-20150209.jar'

foreach ($p in @($remap, $fml, $gson, $lwjgl)) {
    if (-not (Test-Path $p)) { throw "缺少依赖: $p" }
}
if (-not (Test-Path (Join-Path $jdk 'javac.exe'))) { throw "找不到 JDK: $jdk" }

$out   = Join-Path $root 'out'
$build = Join-Path $root 'build'
Remove-Item -Recurse -Force $out   -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force $build -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $out, $build | Out-Null

$cp  = "$remap;$fml;$gson;$lwjgl"
$src = Get-ChildItem -Recurse -Filter '*.java' (Join-Path $root 'src') | ForEach-Object { $_.FullName }

Write-Host "[1/3] javac 编译 $($src.Count) 个源文件 ..."
& (Join-Path $jdk 'javac.exe') -encoding UTF-8 --release 17 -proc:none -cp $cp -d $out $src
if ($LASTEXITCODE -ne 0) { throw "javac 失败 (exit=$LASTEXITCODE)" }

Write-Host "[2/3] 打包资源 ..."
$res = Join-Path $root 'src\main\resources'
$jar = Join-Path $build 'mite-rooted-0.1.0.jar'
& (Join-Path $jdk 'jar.exe') cf $jar -C $out . -C $res .
if ($LASTEXITCODE -ne 0) { throw "jar 失败 (exit=$LASTEXITCODE)" }

Write-Host "[3/3] 完成:"
Get-Item $jar | Select-Object Length, FullName | Format-Table -AutoSize
Write-Host ""
Write-Host "安装：把 build\mite-rooted-0.1.0.jar 复制到"
Write-Host "  (版本隔离关闭) $mc\mods\"
Write-Host "  (版本隔离开启) $mc\versions\1.6.4-MITE\mods\"
Write-Host "⚠️ 同一个 mod id 只能放一个包：换包前先删掉 mods\ 里的旧包，否则启动失败。"
