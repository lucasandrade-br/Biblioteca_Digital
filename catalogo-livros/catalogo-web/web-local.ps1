param(
    [ValidateSet('install', 'start', 'test', 'build')]
    [string]$Action = 'start'
)

$localNode = Join-Path $PSScriptRoot '.runtime/node.exe'
$nodeCandidates = @($localNode) + @(Get-Command node.exe -All -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source)
$node = $nodeCandidates | Where-Object {
    if (!(Test-Path -LiteralPath $_ -PathType Leaf)) { return $false }
    $versionText = & $_ --version
    $version = $null
    [version]::TryParse(($versionText -replace '^v', ''), [ref]$version) -and (
        ($version.Major -eq 22 -and $version -ge [version]'22.22.3') -or
        ($version.Major -eq 24 -and $version -ge [version]'24.15.0') -or
        $version.Major -ge 26
    )
} | Select-Object -First 1

if (!$node) {
    throw 'Use Node.js 22.22.3+ (22.x), 24.15.0+ (24.x) ou 26+.'
}

$npm = Get-Command npm.cmd -ErrorAction Stop
$npmCli = Join-Path (Split-Path -Parent $npm.Source) 'node_modules/npm/bin/npm-cli.js'
if (!(Test-Path -LiteralPath $npmCli -PathType Leaf)) {
    throw 'O npm precisa estar disponivel na instalacao do Node.js.'
}

$previousPath = $env:Path
$previousAnalytics = $env:NG_CLI_ANALYTICS
$env:Path = "$(Split-Path -Parent $node);$previousPath"
$env:NG_CLI_ANALYTICS = 'false'

Push-Location -LiteralPath $PSScriptRoot
try {
    if ($Action -eq 'install') {
        & $node $npmCli ci --cache .npm-cache @args
    } else {
        & $node $npmCli --cache .npm-cache run $Action -- @args
    }
    $status = $LASTEXITCODE
} finally {
    Pop-Location
    $env:Path = $previousPath
    $env:NG_CLI_ANALYTICS = $previousAnalytics
}
exit $status
