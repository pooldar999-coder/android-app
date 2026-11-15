# PhoneGuard per-app firewall — implementation plan

## User requirements (must not regress)
- No VPN-based firewall backend. The owner uses a personal VPN and PhoneGuard must not take over Android's single VPN slot.
- No root requirement.
- Per-application internet blocking, with separate Wi-Fi and mobile-data controls only where the OS/backend can actually enforce them.
- Persian UI, explicit effective-state reporting, saved rules, and safe recovery/unblock path.
- Target test device: POCO X5, Android 14; record exact HyperOS/MIUI build during testing.

## Technical decision
Use a Shizuku-mediated, non-VPN backend as the primary design candidate. Shizuku is only the privileged command bridge; firewall rules must be enforced by Android's system networking layer. Do not implement a VPNService fallback and do not request VPN consent.

Do not copy ShizuWall implementation code into this repository without first deciding to adopt its GPL-3.0-only licensing obligations. Its published documentation says Android 11+ through Shizuku, LibADB, or root. It is a compatibility/reference target, not proof that this POCO X5 build will work.

Do not use NetControl's cmd connectivity set-package-networking-enabled implementation on Android 14: its own documented requirements say those commands are available on Android 15+, not Android 14.

## Required implementation phases
1. Capability probe (no changes): detect Android API level, Shizuku installed/running/permission state, shell command availability, and exact backend capability. Report unsupported states plainly; never show a rule as active merely because a command was attempted.
2. Backend adapter: isolate privileged operations behind an interface. Implement only a backend whose commands are verified on API 34. Do not assume a shell command exists based on Android version alone.
3. Rules and persistence: store package name, per-transport rule, last requested state, and last verified effective state. Reconcile after app updates/reinstalls and after reboot. Never silently treat persisted preferences as active kernel rules.
4. UI: searchable app list, separate Wi-Fi/mobile-data controls only if the backend truly supports independent transports; otherwise expose a clearly labelled all-network block. Show Persian states: فعال و تأییدشده، در انتظار اعمال، پشتیبانی‌نشده، خطا.
5. Safety: protect PhoneGuard itself, core telephony/system packages, and the user's selected VPN app from accidental blocking by default. Add a global disable/restore action and confirmation before blocking a system app. Never automatically block newly installed apps.
6. Verification: after every write, read back effective state. Include on-device connectivity tests for Wi-Fi and cellular, VPN-connected and VPN-disconnected states, and reboot/re-apply. Test that the user's VPN remains connected and unchanged.
7. Release gate: do not include an enabled firewall control in a release APK until API 34 emulator and real POCO X5/HyperOS tests pass. A debug build or CI success alone is not proof of device-level enforcement.

## Known compatibility caveats
- Android connectivity/firewall interfaces differ by release and OEM build. Android 14 compatibility must be proven on the target device.
- Some system firewall mechanisms apply one combined deny rule to both transports; separate Wi-Fi/mobile controls must not be displayed unless independently enforceable.
- Shizuku authorization and Wireless debugging are user-controlled setup steps. PhoneGuard cannot silently grant them.
- Rules may be cleared by reboot depending on backend. The UI must detect this and require re-application rather than claiming persistence.

## Acceptance checklist
- [ ] No VPNService usage or VPN permission prompt in the firewall feature.
- [ ] Shizuku capability probe correctly reports supported/unsupported on Android 14.
- [ ] Block/unblock is read back from the system after every operation.
- [ ] Wi-Fi block verified with Wi-Fi on.
- [ ] Cellular block verified with Wi-Fi off.
- [ ] Existing personal VPN remains usable throughout.
- [ ] Reboot behaviour and recovery are tested.
- [ ] No critical system package can be blocked accidentally.
- [ ] Persian error messages include the actionable cause.
- [ ] Tests and device/build metadata are recorded before release.

## Research references
- ShizuWall: https://github.com/AhmetCanArslan/ShizuWall
- ShizuWall F-Droid metadata: https://f-droid.org/en/packages/com.arslan.shizuwall/
- NetControl Android 15 requirement: https://github.com/mimbasher/NetControl/
- FireWall Blocks reference: https://github.com/shynoiddev/FireWall-Blocks

This document records implementation requirements and release gates. It does not claim firewall code has already been integrated or that the target phone has been tested.