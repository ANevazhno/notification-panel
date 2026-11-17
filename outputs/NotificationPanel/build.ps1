param(
    [Parameter(Mandatory=$true)][string]$JdkHome,
    [Parameter(Mandatory=$true)][string]$AndroidJar,
    [Parameter(Mandatory=$true)][string]$BuildTools,
    [Parameter(Mandatory=$true)][string]$WorkDir,
    [Parameter(Mandatory=$true)][string]$KeyStore
)
$ErrorActionPreference='Stop'
$JdkHome=(Resolve-Path -LiteralPath $JdkHome).Path
$AndroidJar=(Resolve-Path -LiteralPath $AndroidJar).Path
$BuildTools=(Resolve-Path -LiteralPath $BuildTools).Path
$project=$PSScriptRoot
New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null
$WorkDir=(Resolve-Path -LiteralPath $WorkDir).Path
$classes=Join-Path $WorkDir 'classes'
$gen=Join-Path $WorkDir 'gen'
$dex=Join-Path $WorkDir 'dex'
$tests=Join-Path $WorkDir 'tests'
New-Item -ItemType Directory -Force -Path $classes,$gen,$dex,$tests | Out-Null
function Run([string]$exe,[string[]]$arguments) {
    & $exe @arguments
    if($LASTEXITCODE -ne 0){throw "Tool failed ($LASTEXITCODE): $exe"}
}
$java=Join-Path $JdkHome 'bin\java.exe'
$javac=Join-Path $JdkHome 'bin\javac.exe'
$jar=Join-Path $JdkHome 'bin\jar.exe'
$aapt=Join-Path $BuildTools 'aapt2.exe'
$compiled=Join-Path $WorkDir 'resources.zip'
$unsigned=Join-Path $WorkDir 'unsigned.apk'
$aligned=Join-Path $WorkDir 'aligned.apk'
Run $aapt @('compile','--dir',(Join-Path $project 'res'),'-o',$compiled)
Run $aapt @('link','-I',$AndroidJar,'--manifest',(Join-Path $project 'AndroidManifest.xml'),'--java',$gen,'-o',$unsigned,$compiled)
$sources=@(Get-ChildItem (Join-Path $project 'src'),$gen -Filter '*.java' -Recurse | ForEach-Object {$_.FullName})
$argfile=Join-Path $WorkDir 'javac.args'
$argsLines=@('-encoding','UTF-8','-source','8','-target','8','-classpath',('"'+$AndroidJar.Replace('\','/')+'"'),'-d',('"'+$classes.Replace('\','/')+'"'))
$argsLines+=@($sources|ForEach-Object {'"'+$_.Replace('\','/')+'"'})
[IO.File]::WriteAllLines($argfile,$argsLines,[Text.UTF8Encoding]::new($false))
Run $javac @('@'+$argfile)
Run $javac @('-encoding','UTF-8','-d',$tests,(Join-Path $project 'src\local\notificationpanel\Grouping.java'),(Join-Path $project 'tests\GroupingTest.java'))
Run $java @('-cp',$tests,'GroupingTest')
Run $javac @('-encoding','UTF-8','-d',$tests,(Join-Path $project 'src\local\notificationpanel\MediaDismissal.java'),(Join-Path $project 'tests\MediaDismissalTest.java'))
Run $java @('-cp',$tests,'local.notificationpanel.MediaDismissalTest')
Run $javac @('-encoding','UTF-8','-d',$tests,(Join-Path $project 'src\local\notificationpanel\UpdateCore.java'),(Join-Path $project 'tests\UpdateCoreTest.java'))
Run $java @("-Djava.io.tmpdir=$WorkDir",'-cp',$tests,'UpdateCoreTest')
$classJar=Join-Path $WorkDir 'classes.jar'
Run $jar @("-J-Djava.io.tmpdir=$WorkDir",'cf',$classJar,'-C',$classes,'.')
Run $java @('-cp',(Join-Path $BuildTools 'lib\d8.jar'),'com.android.tools.r8.D8','--lib',$AndroidJar,'--min-api','28','--output',$dex,$classJar)
Run $jar @("-J-Djava.io.tmpdir=$WorkDir",'uf',$unsigned,'-C',$dex,'classes.dex')
Run (Join-Path $BuildTools 'zipalign.exe') @('-f','4',$unsigned,$aligned)
if(!(Test-Path -LiteralPath $KeyStore)){
    $keyParent=Split-Path $KeyStore -Parent
    New-Item -ItemType Directory -Force -Path $keyParent | Out-Null
    Run (Join-Path $JdkHome 'bin\keytool.exe') @('-genkeypair','-keystore',$KeyStore,'-storepass','android','-keypass','android','-alias','panel-dev','-keyalg','RSA','-keysize','2048','-validity','3650','-dname','CN=Local Notification Panel Development','-noprompt')
}
[xml]$manifest=Get-Content -LiteralPath (Join-Path $project 'AndroidManifest.xml')
$version=$manifest.manifest.GetAttribute('versionName','http://schemas.android.com/apk/res/android')
$output=Join-Path (Split-Path $project -Parent) "NotificationPanel-$version.apk"
Run $java @('-jar',(Join-Path $BuildTools 'lib\apksigner.jar'),'sign','--ks',$KeyStore,'--ks-key-alias','panel-dev','--ks-pass','pass:android','--key-pass','pass:android','--out',$output,$aligned)
Run $java @('-jar',(Join-Path $BuildTools 'lib\apksigner.jar'),'verify','--verbose',$output)
Run $aapt @('dump','badging',$output)
Write-Output "Built: $output"
