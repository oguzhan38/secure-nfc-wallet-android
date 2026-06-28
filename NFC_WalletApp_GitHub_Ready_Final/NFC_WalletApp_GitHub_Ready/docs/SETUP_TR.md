# Kurulum Rehberi

Bu dosya, projeyi Android Studio ve local server ile çalıştırmak için kısa kurulum adımlarını içerir.

## 1. Projeyi Android Studio'da Açma

1. ZIP dosyasını çıkar.
2. Android Studio'yu aç.
3. `Open` seçeneği ile ana proje klasörünü seç:

```text
NFC_WalletApp/
```

`server/` klasörünü tek başına Android Studio projesi olarak açma.

## 2. Local Server'ı Başlatma

Windows üzerinde:

```bat
cd server
pip install -r requirements.txt
Start_Server.bat
```

Server açılınca PC üzerinde admin security console görünür.

## 3. APK Alma

Android Studio üzerinden:

```text
Build > Build Bundle(s) / APK(s) > Build APK(s)
```

veya terminal üzerinden:

```bat
.\gradlew assembleDebug
```

APK çıktısı:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 4. Telefonlara Kurulum

Aynı APK'yı iki Android telefona kur.

- Telefon 1: `retailer1 / 1234`
- Telefon 2: `customer1 / 1234`

İki telefon ve PC aynı Wi-Fi ağına bağlı olmalıdır.

## 5. Server IP Bilgisi

Uygulama server'ı otomatik bulamazsa PC'nin yerel IP adresini elle gir.

Örnek:

```text
Server IP: 192.168.1.34
Port: 5000
```

PC IP adresini Windows'ta şu komutla öğrenebilirsin:

```bat
ipconfig
```

`IPv4 Address` değerini kullan.
