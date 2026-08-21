# H JOEL POD - CLEAN NEW PROJECT

Ini project baru dari nol.

## Arsitektur
- UI aplikasi ada di dalam APK (`app/src/main/assets/index.html`).
- Karena UI tidak lagi membuka halaman Google Apps Script, banner biru Google Apps Script tidak muncul.
- Scanner AWB memakai `html5-qrcode` langsung di halaman, mengikuti pola file Outbound yang Anda kirim.
- Area diambil dari backend Apps Script melalui Android bridge.
- Simpan POD juga melalui Android bridge ke Apps Script.
- 4 foto:
  - Timestamp Camera
  - Kamera HP
  - Upload Foto
- Kamera HP: selesai jepret langsung kembali ke form.
- Timestamp Camera: setelah kembali ke H JOEL POD, aplikasi mencoba mengambil foto terbaru dari MediaStore dan langsung memasukkannya ke form.

## Apps Script
1. Buat project Apps Script baru atau pakai project POD yang ada.
2. Hapus Kode.gs lama.
3. Copy isi `AppsScript_Kode.gs` ke `Kode.gs`.
4. Deploy sebagai Web App:
   - Execute as: Me
   - Who has access: Anyone
5. URL yang saat ini tertanam di APK:
   https://script.google.com/macros/s/AKfycbxAKv3UnWrFM2R4s1h0EXrELPpxOwH6ctTrUcOj9h-ExWQkBj_Y_ivnJ86m4QaOhPxV/exec

Jika nanti URL deployment berubah, ganti `WEB_APP_URL` di:
`app/src/main/java/com/hjoel/pod/MainActivity.java`

## GitHub
Buat repository BARU, contoh:
`H-JOEL-POD-FINAL`

Upload SEMUA ISI folder project ini dengan struktur tetap utuh.

Setelah commit:
- buka Actions
- tunggu `Build H JOEL POD APK` hijau
- download artifact `H-JOEL-POD-APK`
- ekstrak ZIP
- install `app-debug.apk`

## Catatan izin Android
Saat pertama memakai scanner:
- Izinkan Kamera.

Saat pertama memakai Timestamp Camera auto-import:
- Izinkan Foto & Video / Photos and videos secara penuh.
