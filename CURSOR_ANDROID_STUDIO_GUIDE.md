# Cursor + Android Studio Integration Guide for Windows 11
## Password Manager Development Workflow

This guide will help you set up a seamless "vibe coding" workflow for your Passbook password manager app using Cursor on Windows 11 alongside Android Studio.

---

## 🚀 Quick Setup Overview

### What You'll Achieve
- Use Cursor for AI-assisted code editing and refactoring
- Keep Android Studio for building, debugging, and emulator management
- Seamless hot-reload development experience
- AI-powered code generation for security features

---

## 📋 Prerequisites

### Required Software
1. **Cursor Desktop App** (Windows 11)
   - Download from: https://cursor.sh
   - Install like any Windows application

2. **Android Studio** (Latest version)
   - For building, debugging, and Android-specific tooling
   - Keep your existing installation

3. **JDK 17+** 
   - Required for your Kotlin Android project
   - Usually comes with Android Studio

---

## 🎯 Recommended Workflow: Two-Editor Setup

### Option 1: Primary Cursor, Secondary Android Studio (RECOMMENDED)

**Use Cursor for:**
- ✅ Writing Kotlin code with AI assistance
- ✅ AI-powered refactoring and code generation
- ✅ Quick file navigation and editing
- ✅ Git operations and version control
- ✅ Security code reviews and suggestions
- ✅ Documentation writing

**Use Android Studio for:**
- ✅ Running the app on emulator/device
- ✅ Debugging with breakpoints
- ✅ Layout inspector and profiler
- ✅ Resource management (drawables, layouts)
- ✅ Gradle builds and dependency management
- ✅ Android-specific refactoring tools

### How to Set It Up

1. **Open Project in Cursor**
   ```bash
   # In Windows Terminal or PowerShell
   cd C:\path\to\your\project-passbook
   cursor .
   ```

2. **Keep Android Studio Open Simultaneously**
   - Open the same project in Android Studio
   - Both editors can watch the same files
   - Changes in Cursor will auto-refresh in Android Studio

3. **Set Up Auto-Save in Cursor**
   - Settings → Editor → Auto Save
   - Enable "Auto Save" for smooth experience

---

## 💡 Cursor Features for Android Development

### 1. AI Chat for Android-Specific Questions

Ask Cursor AI things like:
- "Add biometric authentication to LoginScreen.kt"
- "Create a secure password generator following NIST guidelines"
- "Refactor this ViewModel to use Kotlin Flow instead of LiveData"
- "Add unit tests for CryptoManager.kt"
- "Review this code for security vulnerabilities"

### 2. Composer for Multi-File Edits

Use Cursor's Composer (Ctrl+I) to:
- Generate entire feature implementations
- Refactor across multiple files
- Add comprehensive error handling
- Implement design patterns

Example prompt:
```
@app/src/main/java/com/jcb/passbook/presentation/ui/screens/vault/
Add a password sharing feature with end-to-end encryption
```

### 3. Code Context (@-mentions)

Reference specific files or folders:
```
@SecurityManager.kt How can I improve the root detection logic?
@security/ Add tamper detection to all security components
```

### 4. Terminal Integration

Cursor has built-in terminal:
- `Ctrl+`` to open terminal
- Run Gradle commands directly
- Test your builds without switching to Android Studio

---

## 🔧 Windows 11 Specific Tips

### 1. Windows Terminal Integration
```powershell
# Add Cursor to Windows Terminal
# Open Windows Terminal Settings → Add New Profile
# Command line: C:\Users\YourName\AppData\Local\Programs\Cursor\Cursor.exe
```

### 2. Windows Defender Exclusions
Add these folders to Windows Defender exclusions for better performance:
- Your project directory
- `C:\Users\YourName\.gradle`
- `C:\Users\YourName\.android`

### 3. File Watcher Limits
If you get "File watchers limit reached" errors:
```powershell
# Cursor handles this automatically on Windows, but if needed:
# Restart Cursor with administrator privileges
```

### 4. WSL2 Integration (Optional)
If you want to use Linux tooling:
```powershell
# Install WSL2
wsl --install

# Open project in WSL from Cursor
# Use Remote-WSL extension
```

---

## 🎨 Vibe Coding Setup for Password Manager

### Recommended Cursor Settings

1. **Theme**: Dark+ or One Dark Pro (matches Android Studio)
2. **Font**: JetBrains Mono or Fira Code with ligatures
3. **Extensions** (if available):
   - Kotlin Language Support
   - Android Studio Extension Pack
   - GitLens (for better git visualization)

### Workspace-Specific AI Rules

Create `.cursorrules` file in your project root:

```
# Password Manager Development Guidelines

## Security First
- Always use parameterized queries for database operations
- Never log sensitive data (passwords, keys, PINs)
- Use secure memory clearing for sensitive strings
- Follow OWASP Mobile Security guidelines

## Code Style
- Follow Kotlin coding conventions
- Use Jetpack Compose best practices
- Prefer Kotlin Coroutines and Flow over callbacks
- Use dependency injection with Hilt

## Testing Requirements
- Write unit tests for all ViewModels
- Add security tests for cryptographic operations
- Test biometric authentication flows
- Mock external dependencies

## Audit Logging
- Log all security-relevant events
- Include timestamps and user context
- Never log sensitive user data
```

---

## 🔄 Daily Development Workflow

### Morning Setup
1. Open Cursor → Open your project folder
2. Open Android Studio → Open same project
3. Start Android Emulator from Android Studio
4. Pull latest changes in Cursor terminal

### Development Loop
1. **Write/Edit Code in Cursor**
   - Use AI to generate boilerplate
   - Get instant suggestions
   - Quick file navigation with Ctrl+P

2. **Build in Android Studio**
   - Click "Run" in Android Studio
   - Or use Cursor terminal: `./gradlew assembleDebug`

3. **Test on Emulator**
   - View changes on Android emulator
   - Use Android Studio debugger if needed

4. **Commit in Cursor**
   - Use Source Control panel (Ctrl+Shift+G)
   - AI-assisted commit messages

---

## 🎯 Practical Examples for Your Passbook App

### Example 1: Adding a New Feature with AI

**In Cursor Composer (Ctrl+I):**
```
Add a password breach detection feature that checks passwords against 
Have I Been Pwned API. Add it to @ItemViewModel.kt and create a new 
service class for the API integration. Include proper error handling 
and user notifications.
```

### Example 2: Security Code Review

**In Cursor Chat:**
```
@security/crypto/ Review all cryptographic implementations for:
1. Use of secure random number generators
2. Proper key derivation functions
3. Secure key storage
4. Memory clearing after use
Suggest improvements following Android security best practices.
```

### Example 3: Refactoring Database Layer

**In Cursor Chat:**
```
@data/local/database/ Migrate all Room DAOs to use Kotlin Flow 
instead of LiveData. Update corresponding repositories and 
ViewModels. Ensure proper error handling and offline-first behavior.
```

### Example 4: Adding Unit Tests

**In Cursor Composer:**
```
@presentation/viewmodel/vault/ItemViewModel.kt Generate comprehensive 
unit tests covering all functions, edge cases, and error scenarios. 
Use MockK for mocking and follow AAA pattern.
```

---

## 🐛 Troubleshooting Common Issues

### Issue: Changes in Cursor Not Reflecting in Android Studio
**Solution:** 
- Enable "Synchronize files on frame activation" in Android Studio
- File → Settings → Appearance & Behavior → System Settings

### Issue: Gradle Build Fails in Cursor Terminal
**Solution:**
- Use Android Studio's embedded JDK
- Set JAVA_HOME: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`

### Issue: Cursor Runs Slowly with Large Android Project
**Solution:**
- Exclude build folders: Add to `.gitignore` and Cursor will respect it
- Disable unused extensions
- Increase Cursor's memory limit in settings

### Issue: Emulator Not Detected
**Solution:**
- Keep Android Studio open (it manages ADB)
- Or add Android SDK to PATH:
  ```powershell
  $env:PATH += ";C:\Users\YourName\AppData\Local\Android\Sdk\platform-tools"
  ```

---

## 🚀 Advanced Tips

### 1. Multi-Module Navigation
Your project structure:
```
project-passbook/
  ├── app/
  │   ├── security/       # Crypto, audit, detection
  │   ├── data/           # Database, repositories
  │   ├── presentation/   # UI, ViewModels
  │   └── core/           # DI, application
```

Use Cursor's fuzzy search (Ctrl+P) for instant navigation:
- Type "LoginSc" → jumps to LoginScreen.kt
- Type "CryptoMan" → jumps to CryptoManager.kt

### 2. AI-Powered Code Generation Shortcuts

Create custom keybindings in Cursor:
- `Ctrl+Shift+T`: "Generate unit tests for this class"
- `Ctrl+Shift+D`: "Add comprehensive documentation"
- `Ctrl+Shift+S`: "Review for security vulnerabilities"

### 3. Git Integration

Cursor's Git features:
- View inline blame annotations
- Stage/unstage changes quickly
- AI-generated commit messages
- Visual diff viewer

### 4. Collaborative Coding

If working with a team:
- Use Cursor's Live Share equivalent (if available)
- Share AI chat sessions for pair programming
- Create team-wide `.cursorrules` for consistency

---

## 📱 Deployment Workflow

### Building Release APK

**In Cursor Terminal:**
```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing config)
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug
```

**Or use Android Studio:**
- Build → Generate Signed Bundle/APK
- Use Android Studio's signing wizard

---

## 🎓 Learning Resources

### Cursor-Specific
- Cursor Documentation: https://docs.cursor.sh
- Cursor Community: https://forum.cursor.sh
- AI Prompting Tips: https://docs.cursor.sh/prompting

### Android Security (for your password manager)
- OWASP MASVS: https://mas.owasp.org
- Android Security Best Practices
- Google's Security Library

### Kotlin & Compose
- Kotlin Coroutines Guide
- Jetpack Compose Pathways
- Android Architecture Components

---

## 🎉 Your Optimal Setup Summary

```
┌─────────────────────────────────────────────────┐
│         Windows 11 Desktop                      │
├─────────────────────────────────────────────────┤
│                                                 │
│  ┌──────────────────┐  ┌──────────────────┐   │
│  │   Cursor (Left)  │  │ Android Studio   │   │
│  │                  │  │    (Right)       │   │
│  │ • Code editing   │  │ • Emulator       │   │
│  │ • AI assistance  │  │ • Debugging      │   │
│  │ • Git ops        │  │ • Profiling      │   │
│  │ • Terminal       │  │ • Build tools    │   │
│  └──────────────────┘  └──────────────────┘   │
│                                                 │
│  ┌──────────────────────────────────────────┐  │
│  │  Windows Terminal (Bottom)               │  │
│  │  • Gradle commands                       │  │
│  │  • Git operations                        │  │
│  └──────────────────────────────────────────┘  │
│                                                 │
│  ┌──────────────────────────────────────────┐  │
│  │  Browser (Documentation/Testing)         │  │
│  └──────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

---

## ✨ Pro Tips for Vibe Coding

1. **Use Cursor's AI Inline** (Ctrl+K)
   - Select code → Ctrl+K → "Add error handling"
   - Instant inline edits without chat

2. **Create Code Snippets**
   - Save common patterns (ViewModels, Compose screens)
   - Quick insert with AI assistance

3. **Leverage Context**
   - Keep relevant files open in Cursor
   - AI uses open files as context

4. **Regular Breaks**
   - Use Pomodoro technique
   - Let AI handle boilerplate while you focus on architecture

5. **Security Reviews**
   - Regularly ask AI to review security-critical code
   - "Review @security/ for vulnerabilities"

---

## 🎯 Next Steps

1. **Try This First Exercise:**
   ```
   Open Cursor, use Composer (Ctrl+I), and paste:
   
   "Analyze the current security implementation in @security/crypto/
   and suggest improvements based on Android 14 security features.
   Focus on biometric authentication and key storage."
   ```

2. **Set Up Your Workspace:**
   - Create `.cursorrules` file (see above)
   - Configure Cursor settings for Android
   - Set up split screen with Android Studio

3. **Start Building:**
   - Pick a feature from your backlog
   - Use AI to scaffold the implementation
   - Test on emulator
   - Iterate with AI assistance

---

## 📧 Support & Community

- Cursor Support: support@cursor.sh
- Android Studio Issues: https://issuetracker.google.com/issues?q=componentid:192708
- Your Team: Collaborate using shared Cursor settings

---

**Happy Vibe Coding! 🚀**

Your password manager startup is in good hands with this setup. Focus on building secure, user-friendly features while Cursor handles the heavy lifting.
