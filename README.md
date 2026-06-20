# Helga | WiFi FTP Server (Android)

A clean, Material 3 Android app named **Helga** that turns your phone into a WiFi FTP server,
so you can pull files straight into Windows 11 File Explorer no cable, no app on the PC.

## How it works

- Embeds [Apache MINA FtpServer](https://mina.apache.org/ftpserver-project/) and runs it as a
  foreground service (`FtpServerService.kt`), serving your phone's shared storage.
- The UI (`MainActivity.kt`) shows the live `ftp://ip:port` address, credentials, and a QR code.
- Default login: `android` / `1234` (change in `FtpServerService.kt` companion object,
  or wire up a settings screen if you want it configurable from the UI).
- Default port: `2121` (some routers/ISPs block 21, so 2121 is safer to avoid conflicts).

## Using it

1. Make sure your phone and your Windows 11 PC are on the **same WiFi network**.
2. Open the app, tap **Start Server**.
3. On Windows: open **File Explorer**, click the address bar, type the `ftp://...`
   address shown in the app, press Enter.
4. Drag and drop files between the FTP window and any folder on your PC.

> Note: Windows Explorer's built in FTP client is read/browse friendly but can be flaky for
> uploads. If you hit issues, use **WinSCP** or **FileZilla** on Windows instead same
> `ftp://ip:port` address, same credentials, much more reliable for two-way transfers.

## Android 11+ storage note

To let the FTP server read/write your whole shared storage (not just app specific folders),
the app requests `MANAGE_EXTERNAL_STORAGE`. On first run, Android will prompt you to grant
"All files access" in Settings accept it, or the FTP root will be empty/restricted.
