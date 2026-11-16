# Direct SMS read probe — 2026-11-05

Package: local.smstest. APK and source kept here, not published.
Only READ_SMS is requested; no INTERNET, SEND_SMS, RECEIVE_SMS, write capability,
SMS-role components, background service, or backups. Display is FLAG_SECURE.
Queries newest inbox SMS on explicit button press; does not mark it read.

CPH2723 result: UI reported successful direct reading with nonempty body.
READ_SMS app-op: allow. Default SMS role before and after:
com.google.android.apps.messaging (unchanged).
Temporary UI dumps were removed. Do not store SMS bodies in test artifacts.
The app remains installed for the user's review; this is a separate probe,
not an integration into notification panel 0.11.6. RCS is not covered.
