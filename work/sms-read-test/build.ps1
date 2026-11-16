$ErrorActionPreference='Stop'
$base=$env:PANEL_TOOLCHAIN_ROOT
if (-not $base) { throw 'Set PANEL_TOOLCHAIN_ROOT to the directory containing toolchain/ and panel/panel-dev.jks' }
$jdk="$base/toolchain/jdk/jdk-17.0.20.1+1/bin"
$bt="$base/toolchain/buildtools/android-16"
$android="$base/toolchain/platform/android-36/android.jar"
$stage=Join-Path $env:TEMP 'sms-read-probe'
New-Item -ItemType Directory -Force "$stage/classes","$stage/dex" | Out-Null
Copy-Item "$PSScriptRoot/SmsReadTest.java","$PSScriptRoot/AndroidManifest.xml" $stage -Force
function Run($exe,$argsList){& $exe @argsList; if($LASTEXITCODE -ne 0){throw "Failed $exe"}}
Run "$jdk/javac.exe" @('-encoding','UTF-8','-source','8','-target','8','-classpath',$android,'-d',"$stage/classes","$stage/SmsReadTest.java")
Run "$jdk/jar.exe" @('cf',"$stage/classes.jar",'-C',"$stage/classes",'.')
Run "$jdk/java.exe" @('-cp',"$bt/lib/d8.jar",'com.android.tools.r8.D8','--lib',$android,'--min-api','30','--output',"$stage/dex","$stage/classes.jar")
Run "$bt/aapt2.exe" @('link','-I',$android,'--manifest',"$stage/AndroidManifest.xml",'-o',"$stage/unsigned.apk")
Run "$jdk/jar.exe" @('uf',"$stage/unsigned.apk",'-C',"$stage/dex",'classes.dex')
Run "$bt/zipalign.exe" @('-f','4',"$stage/unsigned.apk","$stage/aligned.apk")
Run "$jdk/java.exe" @('-jar',"$bt/lib/apksigner.jar",'sign','--ks',"$base/panel/panel-dev.jks",'--ks-key-alias','panel-dev','--ks-pass','pass:android','--out',"$stage/test.apk","$stage/aligned.apk")
Run "$jdk/java.exe" @('-jar',"$bt/lib/apksigner.jar",'verify',"$stage/test.apk")
Copy-Item "$stage/test.apk" "$PSScriptRoot/SmsReadTest.apk" -Force
