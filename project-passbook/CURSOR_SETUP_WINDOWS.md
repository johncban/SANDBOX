# How to Install and Connect Cursor to GitHub on Windows 11

This guide outlines the steps to install the Cursor editor and configure it with GitHub on a Windows 11 machine.

## Prerequisites

- A [GitHub account](https://github.com/).
- Windows 11 installed.

## Step 1: Download and Install Cursor

1.  Navigate to the official [Cursor website](https://cursor.com/).
2.  Click on the **Download for Windows** button.
3.  Once the installer (`Cursor Setup x.x.x.exe`) is downloaded, double-click it to run.
4.  Follow the on-screen installation prompts. Cursor will automatically install to your local user `AppData` folder and add itself to your PATH.

## Step 2: Initial Setup

1.  Launch **Cursor** from the Start menu.
2.  On the welcome screen, you can choose to import settings from VS Code if you have it installed.
3.  Proceed through the setup wizard (Keyboard, AI settings, etc.).

## Step 3: Connect to GitHub

### 1. Sign in to Cursor
*   Click on the **Settings** (gear icon) > **Sign In** (or the account icon in the activity bar).
*   Choose **Sign in with GitHub**. This links your Cursor editor identity with your GitHub account.

### 2. Configure Git Integration
Cursor comes with built-in Git support. To clone, push, and pull repositories, you need to authenticate Git with GitHub.

**Method A: Using the Visual Interface**
1.  Open the Command Palette (`Ctrl+Shift+P`).
2.  Type `GitHub: Sign In` and select it.
3.  Follow the browser prompt to authorize Cursor to access your GitHub repositories.

**Method B: Using GitHub CLI (Recommended)**
If you prefer the command line:
1.  Open the integrated terminal in Cursor (`Ctrl+``).
2.  Install GitHub CLI if not already installed (via Winget):
    ```powershell
    winget install GitHub.cli
    ```
3.  Restart the terminal, then run:
    ```powershell
    gh auth login
    ```
4.  Follow the interactive prompts:
    *   Select **GitHub.com**.
    *   Select **HTTPS**.
    *   Select **Yes** to authenticate with your web browser.
    *   Copy the one-time code and paste it into the browser window that opens.

## Step 4: Verify Connection

1.  Open the Command Palette (`Ctrl+Shift+P`).
2.  Run `Git: Clone`.
3.  Select **Clone from GitHub**.
4.  If connected successfully, you should see a list of your repositories. Select one to clone it to your machine.

You are now ready to code with Cursor and GitHub on Windows 11!
