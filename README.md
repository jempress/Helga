# Helga — WiFi FTP Server (Android)

Android app that turns your phone into a WiFi FTP server
so you can pull files straight into Windows 11 File Explorer no cable no app on the PC.

## How it works

- Embeds [Apache MINA FtpServer](https://mina.apache.org/ftpserver-project/) and runs it as a
  foreground service (`FtpServerService.kt`), serving your phone's shared storage.
- The UI (`MainActivity.kt`) shows the live `ftp://ip:port` address credentials and a QR code.
- Default login: `android` / `1234` (change in `FtpServerService.kt` or configurable from the UI.
- Default port: `2121` (some routers/ISPs block 21 so 2121 is safer to avoid conflicts).

## Using it

1. Make sure your phone and your Windows 11 PC are on the **same WiFi network**.
2. Open the app tap **Start Server**.
3. On Windows: open **File Explorer**, click the address bar, type the `ftp://...`
   address shown in the app, press Enter.
4. Drag and drop files between the FTP window and any folder on your PC.

> Note: Windows Explorer's built in FTP client is read/browse friendly but can be flaky for
> uploads. If you hit issues, use **WinSCP** or **FileZilla** on Windows instead same
> `ftp://ip:port` address same credentials much more reliable for two way transfers.

## What's new (v1.1)

- **Configurable settings** change username, password, and port right from the app (tap the gear icon).
- **Live connected clients counter** while the server runs.
- **Battery optimization exemption prompt** many phones silently kill background apps, the app now detects this and offers a one tap fix.
- **Keep screen on** toggle while the server is active.
- **Auto start on boot** toggle backed by a boot receiver.
- **Broader device support** minSdk lowered to 21 (Android 5.0+) with core library desugoring so it installs on much older phones too.
- **Smaller, optimized release build** R8 minification + resource shrinking enabled for release builds with proguard rules tuned to keep the FTP server's reflection based wiring intact.

## Fixing "Windows cannot access this file, make sure the path is correct"


This error almost always means one of two things:

1. **Explorer tried to log in anonymously** instead of using your username/password.
   Fix: tap **Copy address with login** in the app it copies
   `ftp://android:1234@<ip>:<port>` (credentials baked into the URL) instead of the
   plain address. Paste that directly into Explorer's address bar.
2. **The FTP data connection (passive mode) got blocked.** Explorer connects on the
   control port fine then opens a *second* connection to transfer file listings/data
   if that's blocked, you get exactly this error. The app now pins passive mode ports to
   `2300–2400` and advertises the phone's own LAN IP which fixes this in almost all
   home/hotspot setups.

If Explorer still won't cooperate after both fixes, it's a know issue with Explorer's FTP
client specifically switch to **WinSCP** or **FileZilla** on the Windows side paste the
same address with login and it'll connect immediately.

## Android 11+ storage note

To let the FTP server read/write your whole shared storage (not just app specific folders)
the app requests `MANAGE_EXTERNAL_STORAGE`. On first run Android will prompt you to grant
"All files access" in Settings accept it or the FTP root will be empty/restricted.

