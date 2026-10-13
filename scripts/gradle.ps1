[CmdletBinding(PositionalBinding = $false)]
param(
    [string]$JavaHome = 'C:\Program Files\Java\dragonwell-8.16.17',
    [Parameter(Position = 0, ValueFromRemainingArguments = $true)]
    [string[]]$GradleArguments = @('build')
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$javaExecutable = Join-Path $JavaHome 'bin/java.exe'
$wrapperJar = Join-Path $projectRoot 'gradle/wrapper/gradle-wrapper.jar'
Push-Location $projectRoot
try {
    & $javaExecutable "-Dorg.gradle.java.home=$JavaHome" `
        -classpath $wrapperJar org.gradle.wrapper.GradleWrapperMain `
        --gradle-user-home (Join-Path $projectRoot '.gradle-user-home') `
        --no-daemon @GradleArguments
    if ($LASTEXITCODE -ne 0) { throw "Gradle exited with $LASTEXITCODE" }
} finally {
    Pop-Location
}
