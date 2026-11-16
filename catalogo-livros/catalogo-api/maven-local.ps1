$jdkCandidates = @($env:JAVA_HOME) + @(Get-ChildItem -Path "$env:USERPROFILE/.vscode/extensions/redhat.java-*/jre/25*" -Directory -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -ExpandProperty FullName)
$jdk = $jdkCandidates | Where-Object {
    $_ -and
    (Test-Path -LiteralPath (Join-Path $_ 'bin/javac.exe') -PathType Leaf) -and
    (Test-Path -LiteralPath (Join-Path $_ 'release') -PathType Leaf) -and
    (Select-String -LiteralPath (Join-Path $_ 'release') -Pattern '^JAVA_VERSION="25(?:[."]|-)')
} | Select-Object -First 1

if (!$jdk) {
    throw 'Configure JAVA_HOME com o caminho de um JDK 25 e execute novamente.'
}

$previousJavaHome = $env:JAVA_HOME
$previousMavenHome = $env:MAVEN_USER_HOME
$env:JAVA_HOME = $jdk
$env:MAVEN_USER_HOME = Join-Path $PSScriptRoot '.maven-cache'
$repository = Join-Path $env:MAVEN_USER_HOME 'repository'

Push-Location -LiteralPath $PSScriptRoot
try {
    & .\mvnw.cmd "-Dmaven.repo.local=$repository" @args
    $status = $LASTEXITCODE
} finally {
    Pop-Location
    $env:JAVA_HOME = $previousJavaHome
    $env:MAVEN_USER_HOME = $previousMavenHome
}
exit $status
