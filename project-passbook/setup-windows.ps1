# Passbook Development Environment Setup Script for Windows 11
# Run this script in PowerShell to check and configure your development environment

Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "Passbook Dev Environment Setup" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host ""

# Function to check if a command exists
function Test-CommandExists {
    param($command)
    $null = Get-Command $command -ErrorAction SilentlyContinue
    return $?
}

# Function to get version from command
function Get-CommandVersion {
    param($command, $versionArg = "--version")
    try {
        $version = & $command $versionArg 2>&1 | Select-Object -First 1
        return $version
    } catch {
        return "Unable to determine version"
    }
}

# Check Prerequisites
Write-Host "Checking Prerequisites..." -ForegroundColor Yellow
Write-Host ""

# Check Java
Write-Host "1. Java JDK:" -ForegroundColor White
if (Test-CommandExists java) {
    $javaVersion = Get-CommandVersion java "-version"
    Write-Host "   ✓ Java found: $javaVersion" -ForegroundColor Green
    if ($env:JAVA_HOME) {
        Write-Host "   ✓ JAVA_HOME set to: $env:JAVA_HOME" -ForegroundColor Green
    } else {
        Write-Host "   ⚠ JAVA_HOME not set (will use Android Studio's JDK)" -ForegroundColor Yellow
    }
} else {
    Write-Host "   ✗ Java not found in PATH" -ForegroundColor Red
    Write-Host "   → Install from Android Studio or download JDK 17+" -ForegroundColor Yellow
}
Write-Host ""

# Check Android SDK
Write-Host "2. Android SDK:" -ForegroundColor White
$androidHome = $env:ANDROID_HOME
$androidSdkRoot = $env:ANDROID_SDK_ROOT

if ($androidHome -or $androidSdkRoot) {
    $sdkPath = if ($androidHome) { $androidHome } else { $androidSdkRoot }
    Write-Host "   ✓ Android SDK found at: $sdkPath" -ForegroundColor Green
    
    # Check for ADB
    $adbPath = Join-Path $sdkPath "platform-tools\adb.exe"
    if (Test-Path $adbPath) {
        Write-Host "   ✓ ADB found" -ForegroundColor Green
    } else {
        Write-Host "   ⚠ ADB not found" -ForegroundColor Yellow
    }
} else {
    Write-Host "   ⚠ ANDROID_HOME not set" -ForegroundColor Yellow
    $defaultPath = "$env:LOCALAPPDATA\Android\Sdk"
    if (Test-Path $defaultPath) {
        Write-Host "   → Found SDK at: $defaultPath" -ForegroundColor Yellow
        Write-Host "   → Consider setting ANDROID_HOME environment variable" -ForegroundColor Yellow
    } else {
        Write-Host "   → Install Android Studio to get Android SDK" -ForegroundColor Yellow
    }
}
Write-Host ""

# Check Git
Write-Host "3. Git:" -ForegroundColor White
if (Test-CommandExists git) {
    $gitVersion = Get-CommandVersion git
    Write-Host "   ✓ Git found: $gitVersion" -ForegroundColor Green
} else {
    Write-Host "   ✗ Git not found" -ForegroundColor Red
    Write-Host "   → Install from: https://git-scm.com/download/win" -ForegroundColor Yellow
}
Write-Host ""

# Check Cursor
Write-Host "4. Cursor:" -ForegroundColor White
if (Test-CommandExists cursor) {
    Write-Host "   ✓ Cursor command found" -ForegroundColor Green
} else {
    $cursorPath = "$env:LOCALAPPDATA\Programs\Cursor\Cursor.exe"
    if (Test-Path $cursorPath) {
        Write-Host "   ✓ Cursor installed at: $cursorPath" -ForegroundColor Green
        Write-Host "   ⚠ 'cursor' command not in PATH" -ForegroundColor Yellow
        Write-Host "   → Add to PATH: $env:LOCALAPPDATA\Programs\Cursor\resources\app\bin" -ForegroundColor Yellow
    } else {
        Write-Host "   ✗ Cursor not found" -ForegroundColor Red
        Write-Host "   → Install from: https://cursor.sh" -ForegroundColor Yellow
    }
}
Write-Host ""

# Check Android Studio
Write-Host "5. Android Studio:" -ForegroundColor White
$asPath = "C:\Program Files\Android\Android Studio\bin\studio64.exe"
if (Test-Path $asPath) {
    Write-Host "   ✓ Android Studio found" -ForegroundColor Green
} else {
    # Check alternative location
    $asPath2 = "$env:LOCALAPPDATA\Programs\Android Studio\bin\studio64.exe"
    if (Test-Path $asPath2) {
        Write-Host "   ✓ Android Studio found" -ForegroundColor Green
    } else {
        Write-Host "   ✗ Android Studio not found" -ForegroundColor Red
        Write-Host "   → Install from: https://developer.android.com/studio" -ForegroundColor Yellow
    }
}
Write-Host ""

# Check Gradle
Write-Host "6. Gradle:" -ForegroundColor White
$gradlewPath = ".\gradlew.bat"
if (Test-Path $gradlewPath) {
    Write-Host "   ✓ Gradle wrapper found (./gradlew.bat)" -ForegroundColor Green
    
    # Try to get Gradle version
    Write-Host "   → Testing Gradle wrapper..." -ForegroundColor Cyan
    try {
        $gradleVersion = & $gradlewPath --version 2>&1 | Select-String "Gradle" | Select-Object -First 1
        Write-Host "   ✓ $gradleVersion" -ForegroundColor Green
    } catch {
        Write-Host "   ⚠ Could not execute Gradle wrapper" -ForegroundColor Yellow
        Write-Host "   → Make sure JAVA_HOME is set correctly" -ForegroundColor Yellow
    }
} else {
    Write-Host "   ✗ Gradle wrapper not found" -ForegroundColor Red
    Write-Host "   → Run from project root directory" -ForegroundColor Yellow
}
Write-Host ""

# Check Project Structure
Write-Host "7. Project Structure:" -ForegroundColor White
if (Test-Path "app\build.gradle.kts") {
    Write-Host "   ✓ Android app module found" -ForegroundColor Green
} else {
    Write-Host "   ✗ app/build.gradle.kts not found" -ForegroundColor Red
    Write-Host "   → Make sure you're in the project root" -ForegroundColor Yellow
}

if (Test-Path "app\src\main\AndroidManifest.xml") {
    Write-Host "   ✓ AndroidManifest.xml found" -ForegroundColor Green
} else {
    Write-Host "   ✗ AndroidManifest.xml not found" -ForegroundColor Red
}
Write-Host ""

# Environment Setup Recommendations
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "Recommended Setup Actions" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Quick Setup Commands:" -ForegroundColor Yellow
Write-Host ""

# Suggest JAVA_HOME setup
if (-not $env:JAVA_HOME) {
    $asJdkPath = "C:\Program Files\Android\Android Studio\jbr"
    if (Test-Path $asJdkPath) {
        Write-Host "→ Set JAVA_HOME (current session):" -ForegroundColor White
        Write-Host "  `$env:JAVA_HOME = '$asJdkPath'" -ForegroundColor Gray
        Write-Host ""
    }
}

# Suggest ANDROID_HOME setup
if (-not $androidHome) {
    $defaultSdk = "$env:LOCALAPPDATA\Android\Sdk"
    if (Test-Path $defaultSdk) {
        Write-Host "→ Set ANDROID_HOME (current session):" -ForegroundColor White
        Write-Host "  `$env:ANDROID_HOME = '$defaultSdk'" -ForegroundColor Gray
        Write-Host ""
    }
}

# Suggest adding to PATH
Write-Host "→ Add Android SDK tools to PATH (current session):" -ForegroundColor White
Write-Host "  `$env:PATH += ';C:\Users\[YourName]\AppData\Local\Android\Sdk\platform-tools'" -ForegroundColor Gray
Write-Host ""

# Test build
Write-Host "→ Test Gradle build:" -ForegroundColor White
Write-Host "  .\gradlew clean assembleDebug" -ForegroundColor Gray
Write-Host ""

# Open in Cursor
Write-Host "→ Open project in Cursor:" -ForegroundColor White
Write-Host "  cursor ." -ForegroundColor Gray
Write-Host ""

# Windows Defender Exclusions
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "Performance Optimization (Optional)" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "For better build performance, consider adding these folders" -ForegroundColor White
Write-Host "to Windows Defender exclusions (requires admin):" -ForegroundColor White
Write-Host ""
Write-Host "  - Your project directory" -ForegroundColor Gray
Write-Host "  - $env:USERPROFILE\.gradle" -ForegroundColor Gray
Write-Host "  - $env:USERPROFILE\.android" -ForegroundColor Gray
Write-Host "  - $env:LOCALAPPDATA\Programs\Cursor" -ForegroundColor Gray
Write-Host ""
Write-Host "To add exclusions:" -ForegroundColor White
Write-Host "  Windows Security → Virus & threat protection → Manage settings" -ForegroundColor Gray
Write-Host "  → Exclusions → Add or remove exclusions" -ForegroundColor Gray
Write-Host ""

# Check connected devices
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "Connected Devices" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host ""

if (Test-CommandExists adb) {
    Write-Host "Checking for connected devices..." -ForegroundColor White
    $devices = & adb devices 2>&1
    if ($devices -match "device$") {
        Write-Host "✓ Devices found:" -ForegroundColor Green
        Write-Host $devices -ForegroundColor Gray
    } else {
        Write-Host "⚠ No devices connected" -ForegroundColor Yellow
        Write-Host "  → Start an emulator in Android Studio" -ForegroundColor Yellow
        Write-Host "  → Or connect a physical device with USB debugging enabled" -ForegroundColor Yellow
    }
} else {
    Write-Host "⚠ ADB not available" -ForegroundColor Yellow
    Write-Host "  → Make sure Android SDK is installed and in PATH" -ForegroundColor Yellow
}
Write-Host ""

# Summary
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "Setup Summary" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host ""

$issues = 0
if (-not (Test-CommandExists java)) { $issues++ }
if (-not (Test-CommandExists git)) { $issues++ }
if (-not (Test-Path ".\gradlew.bat")) { $issues++ }

if ($issues -eq 0) {
    Write-Host "✓ All essential tools are ready!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor White
    Write-Host "  1. Open Android Studio and start an emulator" -ForegroundColor Gray
    Write-Host "  2. Run: cursor ." -ForegroundColor Gray
    Write-Host "  3. In Cursor terminal: .\gradlew assembleDebug" -ForegroundColor Gray
    Write-Host "  4. Read: QUICK_START_WINDOWS.md" -ForegroundColor Gray
} else {
    Write-Host "⚠ Found $issues issue(s) that need attention" -ForegroundColor Yellow
    Write-Host "  → Review the recommendations above" -ForegroundColor Yellow
}
Write-Host ""

# Helpful links
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "Helpful Resources" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Documentation:" -ForegroundColor White
Write-Host "  → CURSOR_ANDROID_STUDIO_GUIDE.md (main guide)" -ForegroundColor Gray
Write-Host "  → QUICK_START_WINDOWS.md (quick reference)" -ForegroundColor Gray
Write-Host "  → .cursorrules (AI coding guidelines)" -ForegroundColor Gray
Write-Host ""
Write-Host "External Resources:" -ForegroundColor White
Write-Host "  → Cursor: https://cursor.sh" -ForegroundColor Gray
Write-Host "  → Android Studio: https://developer.android.com/studio" -ForegroundColor Gray
Write-Host "  → Kotlin: https://kotlinlang.org" -ForegroundColor Gray
Write-Host ""

Write-Host "Setup check complete! 🚀" -ForegroundColor Cyan
Write-Host ""
