param(
    [ValidateSet('test', 'apk', 'all')]
    [string]$Target = 'all'
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
Set-Location $root

# --- Toolchain paths -------------------------------------------------------
$Jdk        = Join-Path $root 'tools\jdk-17'
$BuildTools = Join-Path $env:LOCALAPPDATA 'Android\Sdk\build-tools\35.0.0'
$AndroidJar = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platforms\android-35\android.jar'
$JdkUrl     = 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse'
$KsPath     = Join-Path $root 'keystore\tvmute.keystore'
$KsPass     = 'tvmute123'

function Ensure-Jdk {
    if (Test-Path (Join-Path $Jdk 'bin\javac.exe')) { return }
    $zip = Join-Path $root 'tools\jdk17.zip'
    if (-not (Test-Path $zip)) {
        Write-Host 'Downloading JDK 17 (about 190 MB)...'
        & curl.exe -L --fail --silent --show-error -o $zip $JdkUrl
        if ($LASTEXITCODE -ne 0) { throw 'JDK download failed' }
    }
    Write-Host 'Extracting JDK...'
    $tmp = Join-Path $root 'tools\jdk-tmp'
    if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
    Expand-Archive -Path $zip -DestinationPath $tmp -Force
    $inner = Get-ChildItem $tmp -Directory | Select-Object -First 1
    if (Test-Path $Jdk) { Remove-Item -Recurse -Force $Jdk }
    Move-Item $inner.FullName $Jdk
    Remove-Item -Recurse -Force $tmp
    $env:JAVA_HOME = $Jdk
    $env:Path = "$Jdk\bin;$env:Path"
}

function Ensure-Keystore {
    if (Test-Path $KsPath) { return }
    New-Item -ItemType Directory -Force (Split-Path $KsPath) | Out-Null
    & (Join-Path $Jdk 'bin\keytool.exe') -genkeypair -keystore $KsPath -alias tvmute `
        -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass $KsPass -keypass $KsPass -dname 'CN=TVMute' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
}

function Invoke-Tests {
    $testSrc = Join-Path $root 'tests\java'
    if (-not (Test-Path $testSrc)) {
        Write-Host 'No tests yet.'
        return
    }
    $out = Join-Path $root 'build\test-classes'
    if (Test-Path $out) { Remove-Item -Recurse -Force $out }
    New-Item -ItemType Directory -Force $out | Out-Null

    $files = @(Get-ChildItem -Path $testSrc -Recurse -Filter *.java | ForEach-Object { $_.FullName })
    $logic = Join-Path $root 'app\java\ru\digit\tvmute\PressStateMachine.java'
    if (Test-Path $logic) { $files += $logic }

    & (Join-Path $Jdk 'bin\javac.exe') -encoding UTF-8 -d $out $files
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }

    & (Join-Path $Jdk 'bin\java.exe') -cp $out ru.digit.tvmute.PressStateMachineTest
    if ($LASTEXITCODE -ne 0) { throw 'Tests failed' }
}

function Invoke-Apk {
    $aapt2     = Join-Path $BuildTools 'aapt2.exe'
    $zipAlign  = Join-Path $BuildTools 'zipalign.exe'
    $apkSigner = Join-Path $BuildTools 'apksigner.bat'
    $d8        = Join-Path $BuildTools 'd8.bat'

    if (-not (Test-Path $AndroidJar)) { throw "android.jar not found: $AndroidJar" }
    if (-not (Test-Path $aapt2)) { throw "aapt2 not found: $aapt2" }

    foreach ($dir in 'build', 'build\classes', 'build\dex', 'build\gen', 'out') {
        New-Item -ItemType Directory -Force (Join-Path $root $dir) | Out-Null
    }
    Remove-Item -Recurse -Force (Join-Path $root 'build\classes'), (Join-Path $root 'build\dex'), (Join-Path $root 'build\gen') -ErrorAction SilentlyContinue
    foreach ($dir in 'build\classes', 'build\dex', 'build\gen') {
        New-Item -ItemType Directory -Force (Join-Path $root $dir) | Out-Null
    }

    & $aapt2 compile --dir (Join-Path $root 'app\res') -o (Join-Path $root 'build\res.zip')
    if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile failed' }

    & $aapt2 link -o (Join-Path $root 'build\base.apk') `
        -I $AndroidJar `
        --manifest (Join-Path $root 'app\AndroidManifest.xml') `
        --java (Join-Path $root 'build\gen') `
        --min-sdk-version 28 --target-sdk-version 28 `
        (Join-Path $root 'build\res.zip')
    if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }

    $sources = @(Get-ChildItem -Path (Join-Path $root 'app\java') -Recurse -Filter *.java | ForEach-Object { $_.FullName })
    $sources += @(Get-ChildItem -Path (Join-Path $root 'build\gen') -Recurse -Filter *.java | ForEach-Object { $_.FullName })

    & (Join-Path $Jdk 'bin\javac.exe') -encoding UTF-8 -source 8 -target 8 -Xlint:-options `
        -classpath $AndroidJar -d (Join-Path $root 'build\classes') $sources
    if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

    & (Join-Path $Jdk 'bin\jar.exe') --create --file (Join-Path $root 'build\classes.jar') -C (Join-Path $root 'build\classes') .
    if ($LASTEXITCODE -ne 0) { throw 'jar (classes) failed' }

    & $d8 --lib $AndroidJar --min-api 28 --output (Join-Path $root 'build\dex') (Join-Path $root 'build\classes.jar')
    if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }

    & (Join-Path $Jdk 'bin\jar.exe') --update --file (Join-Path $root 'build\base.apk') -C (Join-Path $root 'build\dex') classes.dex
    if ($LASTEXITCODE -ne 0) { throw 'jar (dex) failed' }

    & $zipAlign -f 4 (Join-Path $root 'build\base.apk') (Join-Path $root 'build\aligned.apk')
    if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

    Ensure-Keystore
    & $apkSigner sign --ks $KsPath --ks-pass "pass:$KsPass" --key-pass "pass:$KsPass" `
        --out (Join-Path $root 'out\TVMute.apk') (Join-Path $root 'build\aligned.apk')
    if ($LASTEXITCODE -ne 0) { throw 'apksigner failed' }

    Write-Host "Build OK: out\TVMute.apk"
}

Ensure-Jdk
$env:JAVA_HOME = $Jdk
$env:Path = "$Jdk\bin;$env:Path"

switch ($Target) {
    'test' { Invoke-Tests }
    'apk'  { Invoke-Apk }
    'all'  { Invoke-Tests; Invoke-Apk }
}
