param(
    [Parameter(Mandatory=$true)][string]$Apk,
    [Parameter(Mandatory=$true)][string]$ApkUrl,
    [Parameter(Mandatory=$true)][long]$VersionCode,
    [Parameter(Mandatory=$true)][string]$VersionName,
    [string]$Notes='Новая версия панели уведомлений.',
    [Parameter(Mandatory=$true)][string]$Output
)
$ErrorActionPreference='Stop'
$uri=[Uri]$ApkUrl
if(!$uri.IsAbsoluteUri -or $uri.Scheme -ne 'https' -or $uri.UserInfo -or $uri.Fragment){throw 'APK URL must be HTTPS without credentials or fragment'}
if($VersionCode -le 0){throw 'VersionCode must be positive'}
$hash=(Get-FileHash -LiteralPath $Apk -Algorithm SHA256).Hash.ToLowerInvariant()
$json=[ordered]@{versionCode=$VersionCode;versionName=$VersionName;apkUrl=$ApkUrl;sha256=$hash;notes=$Notes}|ConvertTo-Json
[IO.File]::WriteAllText([IO.Path]::GetFullPath($Output),$json,[Text.UTF8Encoding]::new($false))
Write-Output "Created $Output"
