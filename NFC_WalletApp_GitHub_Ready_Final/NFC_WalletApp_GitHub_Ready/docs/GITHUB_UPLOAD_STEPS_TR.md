# GitHub'a Yükleme Adımları

## 1. Yeni Repository Oluştur

GitHub'da yeni repository oluştur:

```text
Repository name: secure-nfc-wallet-android
Description: Android NFC wallet prototype with local security server, HCE, APDU logging, replay and tamper detection.
Visibility: Public veya Private
```

Önerilen topics:

```text
android, nfc, hce, network-security, flask, sqlite, jwt, aes, apdu, academic-project
```

## 2. Local Git Başlat

Proje klasöründe terminal aç:

```bat
git init
git add .
git commit -m "Initial commit: Secure NFC Wallet Android project"
```

## 3. Remote Bağla

GitHub'daki repository URL'ini kullan:

```bat
git branch -M main
git remote add origin https://github.com/<username>/secure-nfc-wallet-android.git
git push -u origin main
```

## 4. Kontrol Et

GitHub'da şu dosyaların görünmesi gerekir:

```text
README.md
app/
server/
docs/
build.gradle.kts
settings.gradle.kts
gradlew
gradlew.bat
```

Şu dosyalar repository'ye yüklenmemelidir:

```text
local.properties
.gradle/
build/
app/build/
server/data/*.db
*.apk
*.jks
*.keystore
```
