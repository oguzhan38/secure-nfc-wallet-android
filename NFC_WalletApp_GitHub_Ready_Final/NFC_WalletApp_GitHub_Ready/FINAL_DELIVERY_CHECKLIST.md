# Final Delivery Checklist

Use this checklist before submitting or presenting the project.

## Server

- [ ] `server/Start_Server.bat` starts without error.
- [ ] `server/Run_API_Smoke_Test.bat` passes.
- [ ] Admin console opens.
- [ ] Dashboard shows demo users.
- [ ] Security Logs tab loads.
- [ ] Attack Alerts tab loads.
- [ ] Transactions / Receipts tab loads.

## Android build

- [ ] Project opens in Android Studio.
- [ ] Gradle sync completes.
- [ ] APK builds successfully.
- [ ] App installs on two Android phones.
- [ ] Both phones are on the same Wi-Fi as the PC.
- [ ] NFC is enabled on both phones.
- [ ] Customer phone supports HCE.

## Functional scenarios

- [ ] Retailer login works.
- [ ] Customer login works.
- [ ] Successful NFC payment works.
- [ ] Customer rejection is shown.
- [ ] Timeout test is shown.
- [ ] Receipt history opens for customer.
- [ ] Receipt history opens for retailer.

## Security scenarios

- [ ] Security logs show login events.
- [ ] Security logs show NFC/APDU events.
- [ ] Security logs show encryption/decryption timing.
- [ ] Security logs show HTTP authorization timing.
- [ ] Replay test creates `REPLAY_ATTACK_DETECTED`.
- [ ] Tamper test creates `MITM_TAMPER_DETECTED`.
- [ ] Insufficient balance is rejected.
- [ ] NFC disabled warning is visible.

## Report screenshots

- [ ] Login screen.
- [ ] Retailer POS screen.
- [ ] Customer wallet screen.
- [ ] Successful payment screen.
- [ ] Receipt history screen.
- [ ] Server dashboard.
- [ ] Server security logs.
- [ ] Server attack alerts.
- [ ] Replay rejection.
- [ ] MITM/tamper rejection.
