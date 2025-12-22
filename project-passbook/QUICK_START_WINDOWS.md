# Quick Start Guide - Windows 11 Setup

This is a quick reference for getting started with Cursor + Android Studio on Windows 11.

## 🚀 5-Minute Setup

### Step 1: Install Cursor (if not already done)
```powershell
# Download from: https://cursor.sh
# Run the installer
# Cursor will be installed at: C:\Users\[YourName]\AppData\Local\Programs\Cursor\
```

### Step 2: Open Project in Cursor
```powershell
# Open PowerShell or Windows Terminal
cd C:\path\to\workspace\project-passbook

# Open in Cursor
cursor .

# Or if cursor command not found:
# Right-click project folder → "Open with Cursor"
```

### Step 3: Keep Android Studio Open
- Open Android Studio
- File → Open → Select `project-passbook` folder
- Both editors can now work on the same project simultaneously

### Step 4: Start Emulator
In Android Studio:
- Tools → Device Manager
- Start your preferred emulator

### Step 5: Test the Workflow

**In Cursor:**
1. Open any Kotlin file (e.g., `MainActivity.kt`)
2. Press `Ctrl+L` to open AI chat
3. Try: "Explain what this class does"

**Test Build:**
```powershell
# In Cursor's integrated terminal (Ctrl+`)
.\gradlew assembleDebug
```

**Install on Emulator:**
```powershell
.\gradlew installDebug
```

---

## 🎯 Daily Workflow Commands

### Build Commands (Run in Cursor Terminal)
```powershell
# Clean build
.\gradlew clean

# Debug build
.\gradlew assembleDebug

# Release build
.\gradlew assembleRelease

# Run unit tests
.\gradlew test

# Run instrumented tests
.\gradlew connectedAndroidTest

# Check for dependency updates
.\gradlew dependencyUpdates
```

### Git Commands (Run in Cursor Terminal)
```powershell
# Check status
git status

# Create feature branch
git checkout -b feature/your-feature-name

# Stage changes
git add .

# Commit (Cursor can generate message)
git commit -m "feat: add password strength indicator"

# Push to remote
git push origin feature/your-feature-name
```

---

## 💡 Essential Keyboard Shortcuts

### Cursor Shortcuts
| Shortcut | Action |
|----------|--------|
| `Ctrl+L` | Open AI Chat |
| `Ctrl+I` | Open Composer (multi-file AI) |
| `Ctrl+K` | Inline AI edit |
| `Ctrl+P` | Quick file navigation |
| `Ctrl+Shift+P` | Command palette |
| `Ctrl+`` | Toggle terminal |
| `Ctrl+B` | Toggle sidebar |
| `Ctrl+/` | Toggle comment |
| `Ctrl+D` | Select next occurrence |
| `Ctrl+Shift+F` | Find in files |

### Android Studio Shortcuts (Keep these handy)
| Shortcut | Action |
|----------|--------|
| `Shift+F10` | Run app |
| `Shift+F9` | Debug app |
| `Ctrl+F9` | Build project |
| `Alt+Enter` | Quick fix |
| `Ctrl+Alt+L` | Reformat code |
| `Shift+Shift` | Search everywhere |

---

## 🎨 Recommended Cursor Settings

### Enable Auto-Save
1. `Ctrl+,` to open Settings
2. Search for "auto save"
3. Enable "Auto Save"
4. Set delay to 1000ms

### Set Theme (Optional)
1. `Ctrl+Shift+P` → "Color Theme"
2. Choose "Dark+ (default dark)" or "One Dark Pro"

### Configure Terminal
1. Settings → Features → Terminal
2. Set shell to PowerShell or Windows Terminal
3. Enable "Integrated Terminal: Inherit Env"

---

## 🔥 Common AI Prompts for Your Password Manager

### Feature Development
```
Add a password history feature that shows the last 5 passwords 
for an item. Store them encrypted and add UI to display them 
in ItemDetailsScreen.
```

### Security Review
```
@security/crypto/CryptoManager.kt Review this cryptographic 
implementation for security vulnerabilities. Check for:
1. Proper key storage
2. Secure random number generation  
3. Memory clearing
4. Algorithm choices
```

### Bug Fixing
```
@presentation/ui/screens/auth/LoginScreen.kt The login button 
stays disabled after failed login. Fix this and add proper 
error state handling.
```

### Testing
```
@presentation/viewmodel/shared/UserViewModel.kt Generate 
comprehensive unit tests with MockK. Cover all functions 
and edge cases.
```

### Refactoring
```
@data/repository/ Migrate all LiveData to StateFlow/Flow. 
Update corresponding ViewModels to use collectAsStateWithLifecycle 
in Compose screens.
```

### Documentation
```
@security/audit/AuditChainManager.kt Add comprehensive KDoc 
documentation explaining how the audit chain works and its 
security properties.
```

---

## 🐛 Quick Troubleshooting

### Problem: Gradle build fails in Cursor
**Solution:**
```powershell
# Use Android Studio's JDK
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew clean build
```

### Problem: Cursor is slow
**Solution:**
1. Close unnecessary files
2. Exclude build folders: Add to `.gitignore`:
   ```
   **/build/
   **/.gradle/
   ```
3. Restart Cursor

### Problem: Changes not reflecting in Android Studio
**Solution:**
- In Android Studio: File → Sync Project with Gradle Files
- Or: File → Invalidate Caches → Invalidate and Restart

### Problem: Emulator not detected
**Solution:**
```powershell
# Add ADB to PATH (in PowerShell)
$env:PATH += ";C:\Users\[YourName]\AppData\Local\Android\Sdk\platform-tools"
adb devices
```

### Problem: Cursor can't find cursor command
**Solution:**
```powershell
# Add Cursor to PATH manually
$env:PATH += ";C:\Users\[YourName]\AppData\Local\Programs\Cursor\resources\app\bin"

# Or add permanently:
# System Properties → Environment Variables → PATH → Add path above
```

---

## 📁 Project Structure Quick Reference

```
project-passbook/
├── app/src/main/java/com/jcb/passbook/
│   ├── core/              # App initialization, DI modules
│   ├── data/              # Repositories, database, DAOs
│   ├── presentation/      # UI, screens, ViewModels
│   ├── security/          # Crypto, audit, detection
│   └── utils/             # Helpers, extensions
│
├── app/src/main/res/      # Resources (layouts, drawables)
├── app/build.gradle.kts   # App-level Gradle config
└── build.gradle.kts       # Project-level Gradle config
```

### Key Files to Know
- `CoreApp.kt` - Application class (entry point)
- `MainActivity.kt` - Main activity (Compose host)
- `AppDatabase.kt` - Room database configuration
- `CryptoManager.kt` - Encryption/decryption logic
- `SecurityManager.kt` - Security policies & detection
- `UserViewModel.kt` - User authentication state

---

## 🎯 Your First Task

Try this to get comfortable with the workflow:

1. **Open in Cursor:**
   ```powershell
   cd project-passbook
   cursor .
   ```

2. **Open AI Chat (Ctrl+L):**
   ```
   @presentation/ui/screens/vault/ItemListScreen.kt
   Add a search bar at the top to filter items by title
   ```

3. **Let AI Generate the Code**

4. **Save the File (Ctrl+S)**

5. **Build in Cursor Terminal:**
   ```powershell
   .\gradlew assembleDebug
   ```

6. **Run in Android Studio (Shift+F10)**

7. **Test on Emulator!**

---

## 📚 Resources Bookmarks

### Must-Have Tabs
- [Cursor Docs](https://docs.cursor.sh)
- [Android Developers](https://developer.android.com)
- [Kotlin Docs](https://kotlinlang.org/docs/home.html)
- [Jetpack Compose Docs](https://developer.android.com/jetpack/compose/documentation)
- [Material 3 Guidelines](https://m3.material.io/)

### Security Resources
- [OWASP Mobile Security](https://owasp.org/www-project-mobile-security/)
- [Android Security Best Practices](https://developer.android.com/topic/security/best-practices)
- [Android Keystore System](https://developer.android.com/training/articles/keystore)

---

## ✅ Setup Checklist

- [ ] Cursor installed and project opened
- [ ] Android Studio opened with same project
- [ ] Emulator running
- [ ] Successfully built project: `.\gradlew assembleDebug`
- [ ] Test AI chat (Ctrl+L) works
- [ ] Test AI Composer (Ctrl+I) works
- [ ] Git configured and working
- [ ] Reviewed .cursorrules file
- [ ] Read main guide (CURSOR_ANDROID_STUDIO_GUIDE.md)

---

## 🚀 You're Ready!

Your setup is complete. Start building awesome features for your password manager!

**Pro Tip:** Keep this file open in Cursor while you work for quick reference.

**Remember:** 
- Write code in Cursor (with AI help)
- Build & debug in Android Studio
- Test on emulator
- Commit with Cursor's git integration

Happy coding! 🎉
