[CmdletBinding()]
param(
    [switch]$SkipCoreTests
)
$ErrorActionPreference = 'Stop'

function Get-JavaMajorVersion {
    $versionText = (& java -version 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { throw "Java ไม่สามารถเริ่มทำงานได้ กรุณาติดตั้ง JDK 25 และตั้งค่า PATH/JAVA_HOME`n$versionText" }
    if ($versionText -match 'version "(?:1\.)?(\d+)') { return [int]$Matches[1] }
    if ($versionText -match 'openjdk (\d+)') { return [int]$Matches[1] }
    throw "อ่านเวอร์ชัน Java ไม่ได้:`n$versionText"
}

$javaMajor = Get-JavaMajorVersion
if ($javaMajor -ne 25) {
    throw "ThaiMoney สำหรับ Minecraft 26.3 ต้องใช้ JDK 25; ตรวจพบ Java $javaMajor. ติดตั้ง JDK 25 และตรวจ JAVA_HOME/PATH ก่อนลองใหม่"
}
Write-Host "Java $javaMajor detected." -ForegroundColor Green

$gradleVersion = '9.6.0'
$localGradle = Join-Path $PSScriptRoot ".tools\gradle-$gradleVersion\bin\gradle.bat"
$gradleCommand = Get-Command gradle -ErrorAction SilentlyContinue
if (Test-Path $localGradle) {
    $gradleExe = $localGradle
} elseif ($gradleCommand) {
    $gradleExe = $gradleCommand.Source
    $gradleInfo = (& $gradleExe --version 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0 -or $gradleInfo -notmatch "Gradle $([regex]::Escape($gradleVersion))") {
        Write-Warning "Gradle $gradleVersion is recommended for Minecraft 26.3. The PATH version will be used; version output:`n$gradleInfo"
    }
} else {
    $toolsDir = Join-Path $PSScriptRoot '.tools'
    New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
    $zipPath = Join-Path $toolsDir "gradle-$gradleVersion-bin.zip"
    $url = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"
    Write-Host "Downloading Gradle $gradleVersion from the official Gradle distribution..."
    Invoke-WebRequest -Uri $url -OutFile $zipPath
    Expand-Archive -Path $zipPath -DestinationPath $toolsDir -Force
    Remove-Item $zipPath -Force
    $gradleExe = $localGradle
}

$gradleArgs = @('build')
if ($SkipCoreTests) { $gradleArgs += '-x'; $gradleArgs += 'coreTests' }
Write-Host "Running: $gradleExe $($gradleArgs -join ' ')" -ForegroundColor Cyan
& $gradleExe @gradleArgs
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE. Review the error output above." }
Write-Host "Build succeeded. Check build\libs\ for the mod JAR." -ForegroundColor Green
