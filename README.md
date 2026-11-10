# PhoneGuard

A basic Persian-language Android security inspection utility.

## Checks
- Enabled accessibility services and notification listeners
- Active device-admin apps
- USB debugging state and active VPN transport
- Installed apps with selected dangerous permissions currently granted

## Privacy
No Internet permission is declared and no device information is uploaded.

## Limitations
This is an informational checker, not an antivirus or forensic scanner. A permission or active service is not proof of compromise. It cannot reliably detect all malware, account compromise, or abuse of legitimate app permissions.

## Build
GitHub Actions builds a debug APK on pushes to the main branch and publishes it as a workflow artifact.
