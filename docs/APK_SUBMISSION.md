# Submission APK verification

Prepared 5 November 2026. Original specification section 6.3 (PDF p6) permits a release-signed **or debug APK** connected to live Firebase. The existing debug build is used; no release keystore, dependency upgrade, Firebase target/rules change or application repair was introduced.

## Artifact and source

| Item | Verified value |
|---|---|
| Download | [Application APK](https://github.com/it23585294/clarity-finance/releases/download/v1.0-submission-20261105/Clarity-Finance-1.0-debug.apk) |
| Release | [Clarity Finance 1.0 submission](https://github.com/it23585294/clarity-finance/releases/tag/v1.0-submission-20261105) |
| Checksum file | [APK SHA-256](https://github.com/it23585294/clarity-finance/releases/download/v1.0-submission-20261105/Clarity-Finance-1.0-debug.apk.sha256) |
| Filename / size | `Clarity-Finance-1.0-debug.apk` / **25,853,388 bytes** |
| Version | **1.0**, versionCode **1** |
| Package | `com.clarity.finance` |
| Minimum / target | Android 8.0/API26 / targetSdk 36 |
| Build/signing | Debug-signed, debuggable, **not testOnly**, not an instrumentation APK; signature verified for minSdk 26 |
| Production launcher | `com.clarity.finance.MainActivity`, actual app-icon label **Clarity** |
| Application source commit | `4b7a468928a231a371e793a72e1e37c9f7755c59` |
| SHA-256 | `61512e5e8884077dcef4dde8c94075f54fff18f1c7e572938dc49bed5c453752` |

The expected starting commit was c74e6f1; actual main/origin were already 4b7a468. That intervening user commit changes report/README/IDE/review files, not application/build/test/security inputs. It was preserved and is the exact build source recorded above. The release targets the later documentation-publication commit, whose application inputs are verified identical. The APK is not claimed to embed a Git commit identifier.

## Fresh checks actually executed

- Existing wrapper: `.\gradlew.bat :app:assembleDebug --no-daemon --max-workers=2 --console=plain` succeeded in **14s**, 37 tasks up-to-date. This is a successful incremental build invocation, not a claimed clean recompilation.
- AAPT metadata/manifest/resource inspection: application package/version/minSdk/target/launcher match; no instrumentation declaration and only one launcher. Auxiliary opt-in debug acceptance components are present but are not the app-icon entry point.
- `apksigner verify --min-sdk-version 26 --verbose --print-certs` passed. Only the public certificate was inspected; no signing key/password was read or published. Existing separate-device certificate matched before an in-place update.
- Local approved Firebase configuration, generated debug resources and packaged `project_id` identify **clarity-1c83e**. Production code uses the default Firebase/Firestore instance and no emulator/host override. Firestore's default endpoint is `firestore.googleapis.com`; localhost exceptions in debug network policy do not select an emulator. No configuration file/API credential is committed. This is configuration verification, not a new authenticated live Firestore read.
- A byte-identical external artifact copy and checksum file were prepared; packaged SHA-256 is above. APK/build outputs/signing material are excluded from the Git tree.

## Fresh installation/startup

Separate device: **Clarity_UI_Pilot_API26 / emulator-5564**, Android 8.0.0/API26, x86_64. Before installation and Application startup, read-only saved default-auth/recovery inspection found no authentication or financial proposals. No live session was adopted.

The exact APK was installed using targeted `adb -s emulator-5564 install -r`, with Success and a matching existing certificate. No uninstall, storage clear, recovery dismissal or wipe was used. Tapping the actual **Clarity app icon** opened MainActivity and the **production Sign In screen** (Email, Password, Sign In, Create an account). No ISOLATED ACCEPTANCE banner/host was opened. UI remained responsive; current-process logs and the startup crash window contained no fatal exception/native signal/application crash.

The initial package-only MAIN/LAUNCHER `am start` command could not resolve an intent on this API26 environment and did not start the app. This was a launcher-command limitation, not a demonstrated startup crash; the actual icon check succeeded. Inspection-helper checks were corrected for AAPT's duplicated raw-text display and the existing AGP-generated resource path. No application assertion or production code was weakened/changed.

Post-startup saved-auth/recovery inspection remained empty. No account was registered/signed in and no financial record/recovery operation was created, retried or discarded. No human credentials were needed for signed-out startup. Pixel_6a and DNS/host configuration were not touched; no emulator wipe or app-data clearing was performed. Normal installation/startup can update SDK bookkeeping, so storage is not claimed byte-identical. Logcat was not cleared. Private device/file/log evidence is excluded.

## Publication and limits

The repository is confirmed **private** through authenticated GitHub metadata. No existing release/tag used `v1.0-submission-20261105` at preflight. The release is created at the final intended origin/main documentation commit, then its tag target, asset names/sizes and downloaded hashes are checked. Private release links require authorized GitHub access; publication does not make the project public or invite assessors.

The fresh scope is packaging, signatures/configuration, a data-preserving API26 update and signed-out production startup. No full unit/rules/UI suite or new authenticated/live financial acceptance was run. Earlier **292 unit tests**, final **8 isolated Android cases**, build/lint and **147 reused local rules tests** remain historical evidence in [VERIFICATION.md](VERIFICATION.md). They are not new executions or additive counts.

Known limits remain: approximate rather than stopwatch human capture times, unexecuted TalkBack, historical unresolved API35 native/system instability, no fresh final-render performance benchmark and unexecuted broader live races/migration. This debug APK is not Play Store publication or a hardened release/security certification.

Remaining submission actions: review the current report against the original brief, including truthful contribution/AI-assistance disclosure; verify authorized assessor/LMS access; submit the report/source/APK links or files required by LMS; and attend the scheduled engineering demonstration. The user's intervening report edits are preserved, not rebuilt/revalidated by this APK task. Existing users must retain data and obtain a matching-signed update if a signing conflict occurs.
