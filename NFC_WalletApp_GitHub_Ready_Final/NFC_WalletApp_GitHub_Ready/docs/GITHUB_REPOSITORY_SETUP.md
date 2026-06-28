# GitHub Repository Setup Guide

This document contains the recommended GitHub settings and upload commands for the project.

## Recommended Repository Name

```text
secure-nfc-wallet-android
```

Alternative names:

```text
nfc-wallet-security-demo
android-nfc-wallet-hce-security
secure-nfc-payment-lab
```

## Repository Description

```text
Android NFC wallet prototype with HCE, APDU communication, local security server, replay detection, tamper simulation, security logs and receipt history.
```

## Repository Topics

```text
android
nfc
hce
apdu
network-security
flask
sqlite
jwt
aes
mobile-payment
academic-project
```

## Suggested Visibility

For a university project, either option is acceptable:

- **Public:** good for portfolio/CV usage.
- **Private:** better until the project is fully tested and cleaned.

If the project will be included in a CV, public visibility is usually more useful.

## Initial Git Commands

Open a terminal in the project root folder:

```bat
git init
git add .
git commit -m "Initial commit: Secure NFC Wallet Android project"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/secure-nfc-wallet-android.git
git push -u origin main
```

Replace `YOUR_USERNAME` with your GitHub username.

## Recommended Commit History

If you want a cleaner-looking history, you can commit in stages:

```bat
git init

git add README.md SECURITY.md NOTICE.md .gitignore .gitattributes docs/
git commit -m "Add project documentation and GitHub assets"

git add server/
git commit -m "Add local security server and admin console"

git add app/ build.gradle.kts settings.gradle.kts gradle/ gradlew gradlew.bat
git commit -m "Add Android NFC wallet application"

git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/secure-nfc-wallet-android.git
git push -u origin main
```

## Files That Should Not Be Uploaded

The `.gitignore` file already excludes these, but check before pushing:

```text
local.properties
.gradle/
build/
app/build/
*.apk
*.db
server/data/
.env
__pycache__/
```

## Final Check Before Push

Run:

```bat
git status
```

Make sure no local database, APK, or build folder is staged.

## After Upload

In GitHub repository settings, add these topics:

```text
android, nfc, hce, apdu, network-security, flask, sqlite, jwt, aes, academic-project
```

Also check that images in `docs/images/` are visible inside the README.
