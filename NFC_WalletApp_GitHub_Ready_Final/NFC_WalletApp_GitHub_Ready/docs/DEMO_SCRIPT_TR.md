# Demo Akışı

Bu demo akışı, projeyi hocaya veya jüriye gösterirken izlenebilecek sırayı özetler.

## Ana Demo

1. PC'de `server/Start_Server.bat` çalıştırılır.
2. Admin Security Console açılır.
3. Retailer telefonda `retailer1 / 1234` ile giriş yapılır.
4. Customer telefonda `customer1 / 1234` ile giriş yapılır.
5. Retailer bir fatura oluşturur.
6. Retailer NFC ödeme başlatır.
7. Telefonlar birbirine yaklaştırılır.
8. Customer ödeme isteğini görür ve onaylar.
9. Retailer ekranında success sonucu gösterilir.
10. PC security console üzerinde loglar, nonce, APDU status ve timing değerleri gösterilir.
11. Customer ve retailer receipt history ekranları gösterilir.

## Güvenlik Testleri

### Replay Attack

1. Başarılı bir transaction yapılır.
2. Retailer ekranında `TEST: SON İŞLEMİ REPLAY GÖNDER` butonuna basılır.
3. Server aynı nonce'u tekrar gördüğü için işlemi reddeder.
4. Attack Alerts ekranında replay uyarısı gösterilir.

### MITM / Tamper

1. Retailer ekranında `TEST: MITM / TAMPER PAYLOAD` modu aktif edilir.
2. Yeni ödeme başlatılır.
3. Server payload hash uyuşmazlığını algılar.
4. Transaction reddedilir ve `MITM_TAMPER_DETECTED` logu oluşur.

### Customer Reject

1. Retailer ödeme başlatır.
2. Customer ödeme isteğini reddeder.
3. POS tarafında rejected sonucu görünür.
4. Security logs içinde customer rejection kaydı gösterilir.

### Timeout

1. Retailer ödeme başlatır.
2. Customer timeout testini seçer veya cevap vermez.
3. POS tarafında timeout sonucu gösterilir.
4. Security logs içinde timeout kaydı oluşur.
