# API 26 runtime verification

## Result and scope

Verified on 3 November 2026 (Asia/Colombo), starting from `8f532401dc41dd88bc940e45a015f5614be133cc`, which matched the requested HEAD. No applicable AGENTS.md was found. The original assignment brief, pages 5-6, requires installation and execution on Android 8/API 26 and above, minSdk 26 and targetSdk 34 or higher. The existing minSdk 26, targetSdk 36, compile SDK and all dependency/build-stack versions are unchanged. DI_MIGRATION.md and the existing feature acceptance reports were read.

The production APK installed and launched signed out on actual Android 8.0.0/API 26. Koin startup and Activity recreation passed. The final constrained-device run passed **18 isolated Android cases**, plus the separate **one production startup/recreation case**. The final full Android command passed **230 unit tests**, both APK builds and lint **0 errors / 22 warnings**.

Two demonstrated UI compatibility defects were repaired: keyboard-covered authentication controls and clipped recurring-cancellation conflict feedback. There are no financial, schema, repository, DI-definition, recovery, dependency, security-rule, index or Firebase-target changes. No live account sign-in, registration, financial record operation or recovery retry was performed.

## Preservation and device preparation

The initial tracked tree and index were clean. The four unrelated files remain unchanged and uncommitted: `.vscode/settings.json`, `reporting-review-fixes.diff`, `savings-goals-deadline-fixes.diff` and `savings-goals-recovery-fixes.diff`. A working-file backup and SHA-256 baseline were saved outside the repository before editing. The original Pixel_6a was never targeted by any ADB command, installed onto, stopped, wiped, uninstalled, reconfigured or used for these checks. Its configuration and the DNS workaround script match their baseline hashes. Firebase and local configuration also match their baseline hashes. The shared ADB server was not restarted.

The original SDK contained API 35/37 images but no API 26 image or command-line tools. WHPX acceleration was confirmed usable. Official command-line tools were installed into the separate `D:\clarity-api26-sdk` root, verified against publisher SHA-256 `90AE805D20434428BFFCB699C290860F19BB5F66A67E6B330067E3DE801FB04A`. The existing emulator package was reused through a junction in that new SDK, without upgrading its binaries. AVDs use the separate `D:\clarity-api26-avds` registry, not Pixel_6a's registry.

The official AOSP image is `system-images;android-26;default;x86_64`, revision 1:

- Archive: https://dl.google.com/android/repository/sys-img/android/x86_64-26_r01.zip
- Exact size: 474,178,332 bytes.
- Publisher SHA-1, verified before extraction: `432f149c048bffce7f9de526ec65b336daf7a0a3`.
- Downloaded archive SHA-256: `78A79EB02751B19B6626B785F05D5B0AB3A283596FE9D29E54905090A36FE77D`.

The SDK manager's Java download stalled with zero payload. Direct HTTPS range transfers retained completed chunks, checked each Content-Range/length, then verified the complete publisher checksum before extracting unmodified image files. Network timeouts and one DNS failure interrupted transport. A process-local IPv4 download fallback was used; no host or device DNS/network configuration was changed. The failed SDK-manager attempt had left only an installer marker, not an installed image, in the new SDK. The official archive was installed without overwriting other image files.

AVD creation initially reported the missing emulator package in the separate SDK; referencing the already-installed package resolved it. The legacy image's absent optional devices.xml emitted a tooling warning; creation then exited successfully. Emulator 36.6.11.0/build 15507667 was reused with WHPX and SwiftShader, Vulkan disabled, two CPU cores, headless execution and no snapshot load/save. No wipe-data option was used. The modern emulator enlarged the new AVD data partition to 6 GiB despite the initial 2 GiB request; it is stored outside the repository.

Official tool references: [SDK manager](https://developer.android.com/tools/sdkmanager), [AVD manager](https://developer.android.com/tools/avdmanager), [emulator](https://developer.android.com/studio/run/emulator-commandline).

## Actual devices

| Property | API 26 verification | Separate newer regression |
| --- | --- | --- |
| AVD / serial | Clarity_API26_Verification / emulator-5560 | Clarity_API35_Verification / emulator-5562 |
| Android / API | 8.0.0 / 26 | 15 / 35 |
| Primary ABI | x86_64; also reports x86 | x86_64; also reports arm64-v8a |
| Image | AOSP default, revision 1, no Google Play Services | Existing google_apis_playstore_ps16k image |
| Build | OSR1.180418.004 / 4931640 | AE3A.240806.041 / 12890756 |
| Display | 480 x 800 pixels, 240 dpi, 320 dp width | 480 x 800 pixels, 240 dpi, 320 dp width |
| Text | 130% | 130% |
| RAM / cores | 1536 MiB / 2 | 2048 MiB / 2 |

Software keyboard display with a hardware keyboard was enabled on these new devices only. Window/transition/animator scales were set to zero for deterministic checks. API 26 initially used the hardware-key Nexus S profile; the final run enabled standard on-screen navigation (`qemu.hw.mainkeys=0`), reducing usable height and reproducing the cancellation-dialog defect. The final 18-case API 26 result uses that more constrained configuration.

Running both new AVDs alongside Pixel_6a raised host RAM usage to 97%. Only the new API 35 AVD was stopped normally; final checks were run sequentially with preserved AVD storage. After an API 26 restart, ADB listed the device but its shell transport stalled. A serial-scoped `adb -s emulator-5560 reconnect` restored it. There was no global kill-server or Pixel command. Automatic permission review also timed out on two read-only/test invocations; simplified, explicitly targeted retries succeeded. These were environment/tooling interruptions, not application failures.

## Repairs and regression behavior

**Authentication forms.** On the original production Activity at 320 dp/130% text, the actual software keyboard covered lower login/register fields and actions. Neither form had a scroll container. LoginScreen and RegisterScreen now use an IME-aware BoxWithConstraints with a scrollable Column. Its minimum height preserves centering when space is sufficient; constrained/keyboard-visible content can scroll. Field validation, password clearing, authentication calls and ViewModel logic are unchanged.

The new fake-auth regression actually opens the software keyboard, focuses the confirmation field, scrolls within the exposed area and verifies action bounds above the keyboard before clicking. It then checks local validation and zero backend calls. Its host matches MainActivity's edge-to-edge/adjustResize/Scaffold configuration. Android and Compose IME insets are explicitly awaited rather than assuming Compose idle means the system keyboard has finished layout. Early helper attempts measured before inset agreement; a temporary UiAutomation capture also failed when invoked on the UI thread. The capture was moved for diagnosis and removed from final source. No visibility assertion was removed or relaxed.

**Recurring cancellation dialog.** An existing isolated case exposed hidden conflict text on API 35. The same failure was reproduced on API 26 with software navigation at 320 dp/130% text. The long cancellation explanation left the conflict message outside a non-scrollable dialog body. That body alone now scrolls; the existing Keep/Confirm controls, wording, conflict handling and financial behavior are unchanged. The unchanged existing regression failed before the repair and passed afterward.

**Existing viewport helpers.** The first API 26 fake-only run passed 14 of 17 cases and failed three. A captured semantics tree showed the dashboard's category-card tap actually opened Add Expense because the fixed FAB covered the scroll-to edge. The test now physically taps the card's exposed left side. Savings helpers now scroll composed LazyColumn descendants through the list, and the invalid-form check scrolls to its title before asserting visibility. All original state, balance, conflict and isolation assertions remain. These were test-interaction issues, not calculation/repository defects.

## Production evidence versus isolated evidence

The real MainActivity launched signed out successfully. Blank Sign In and blank registration validation, login/register routing, and actual keyboard scrolling were checked through the production UI using invalid dummy input only. A final production registration check focused Confirm Password, kept the keyboard visible, scrolled Create Account above it and displayed the expected missing-fields error. No valid credential pair was submitted.

Api26ProductionStartupTest is explicitly opt-in through `api26ProductionAudit=true`, asserts API 26 and no authenticated user before launching, and invokes no financial operation. It reads an empty local recovery scope, then uses ActivityScenario.recreate(). The test verifies a new Activity instance with the same Auth ViewModel, global Koin context, FirebaseAuth, DataStore and recovery-store instances. This is **Activity recreation**, not process-death restoration.

The final production app-scoped Logcat capture contained no FATAL EXCEPTION, missing-method/class or Koin construction/startup error. The crash buffer was empty. The x86 CPU-variant/default warning was observed. Compilation and fake repositories are not offered as proof of live Firestore behavior.

The isolated tests use the existing standalone Koin ViewModel graph with fake repository bindings; production platform/repository modules are absent from that graph and unexpected repository calls fail. No global live-repository overrides are used. Their in-memory financial actions are not live financial writes.

## Runtime verification commands and recorded results

ADB below is the installed platform-tools adb.exe. Every actual ADB invocation included the intended serial; no unscoped devices/kill-server command was used.

Device creation/launch used the equivalent explicit options below with per-process SDK/AVD environment variables:

```powershell
avdmanager.bat create avd -n Clarity_API26_Verification -k "system-images;android-26;default;x86_64" -p D:\clarity-api26-avds\Clarity_API26_Verification.avd -d "Nexus S"
emulator.exe -avd Clarity_API26_Verification -port 5560 -sysdir D:\clarity-api26-sdk\system-images\android-26\default\x86_64 -memory 1536 -cores 2 -gpu swiftshader -feature -Vulkan -no-window -no-audio -no-boot-anim -no-snapshot-load -no-snapshot-save
adb -s emulator-5560 shell getprop ro.build.version.release
adb -s emulator-5560 shell getprop ro.build.version.sdk
adb -s emulator-5560 shell getprop ro.product.cpu.abi
adb -s emulator-5560 shell wm size
adb -s emulator-5560 shell wm density
adb -s emulator-5560 shell settings put system font_scale 1.3
adb -s emulator-5560 shell settings put secure show_ime_with_hard_keyboard 1
adb -s emulator-5560 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5560 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5560 shell am start -W -n com.clarity.finance/.MainActivity
```

The baseline APK/test build succeeded before repairs. Focused keyboard builds/runs and the existing single recurring cancellation case were run while diagnosing each change. The keyboard regression failed before the auth repair with no scroll ancestor; the cancellation case failed before its repair and passed afterward.

Final required command, actually executed after both production repairs:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
```

Result: **BUILD SUCCESSFUL in 59 seconds**, **230 tests, 0 failures/errors/skips**, both APKs built, lint **0 errors/22 warnings**. An earlier full run after the auth repair also passed; it was rerun because the subsequent dialog repair changed production code. Test-only build/lint checks were also executed after removing temporary diagnostics. No dependency was upgraded and no existing lint warning was suppressed. A test-only DEPRECATION annotation documents the legacy Display.getRealMetrics call used for physical API 26 keyboard geometry.

Final API 26 fake-only command (after both repairs, software navigation):

```powershell
adb -s emulator-5560 shell am instrument -w -r -e class 'com.clarity.finance.DiViewModelOwnershipTest,com.clarity.finance.PaymentNavigationTest,com.clarity.finance.CryptoNavigationTest,com.clarity.finance.CryptoFeedbackUiTest,com.clarity.finance.DashboardNavigationTest,com.clarity.finance.GoalNavigationTest,com.clarity.finance.RecurringNavigationTest,com.clarity.finance.AuthAccessibilityTest' com.clarity.finance.test/androidx.test.runner.AndroidJUnitRunner
```

Result: **OK (18 tests)**, 67.147 seconds. Coverage includes DI owner reuse/clearing and typed SavedStateHandle arguments; direct/milestone routes; crypto CRUD and operation-derived feedback; dashboard/history/category/backdating/delete/linked-receipt protection/overflow; savings forms/schedule/corrections/pending/rejected/account isolation/deadline revision; recurring AT09/correction/deletion/cancellation/conflicts/pending/account isolation; signed-out validation and actual keyboard usability. These are production screens/ViewModels with isolated data.

Separate final production command:

```powershell
adb -s emulator-5560 shell am instrument -w -r -e class com.clarity.finance.Api26ProductionStartupTest -e api26ProductionAudit true com.clarity.finance.test/androidx.test.runner.AndroidJUnitRunner
```

Result: **OK (1 test)**, 1.906 seconds. Production Activity recreation and process singleton ownership passed.

The same explicit eight-class fake-only selection was executed on the separate API 35 device with `-s emulator-5562`: initially **18 tests, 17 passed, one cancellation-dialog visibility failure**. The latter was subsequently reproduced and fixed on API 26 as described above.

After the repair, an attempted four-case API 35 recurring run passed its first case, then the instrumentation process crashed during the pending/account-switch case. Its crash buffer reports **SIGSEGV/null pointer dereference in ART/JIT-compiled Compose test matcher frames**, including `FiltersKt.hasText`, `SemanticsMatcher.matches` and `SemanticsSelector`. The image reports a 16,384-byte page size. This suggests a runtime/test-environment issue, but its underlying cause is **not proven or fixed**. There was no managed financial/Koin exception in that trace. No JIT setting, dependency or assertion was changed to hide it.

The following two cases were then run individually in fresh instrumentation processes with the final APKs:

```powershell
adb -s emulator-5562 shell am instrument -w -r -e class 'com.clarity.finance.RecurringNavigationTest#templateEditingAndCancellationKeepStopDueCandidatesAndRetainActualHistory' com.clarity.finance.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5562 shell am instrument -w -r -e class 'com.clarity.finance.RecurringNavigationTest#pendingUncertainRejectedAndAccountSwitchStatesNeverBecomeConfirmedSpending' com.clarity.finance.test/androidx.test.runner.AndroidJUnitRunner
```

Results: **OK (1 test)** in 12.539 seconds, and **OK (1 test)** in 9.702 seconds respectively. All 18 distinct newer-device cases therefore have passing executions across the documented runs, but this is **not a clean post-repair full-suite API 35 pass**. The intervening native crash remains an unresolved newer-image/test-runtime limitation. The final API 26 full 18-case run and separate production recreation test passed without that crash. Both newly created AVDs were shut down normally after verification; their storage and installed APKs remain available.

## Reused evidence and limits

The unchanged **124-test Firestore emulator** evidence is reused from the existing reports; it was not rerun because neither rules nor Firestore data-access behavior changed. Earlier DI evidence (230 unit tests and 16 distinct isolated API 37 cases across the documented runs) remains historical and separate, including its initial selector failure and targeted successful rerun. The 230-unit and API 26 results were executed during the runtime verification pass, not copied from the earlier DI results. They are reused evidence for finalization.

No API 26 production-account login, successful registration, live Firestore connectivity/payment/financial acceptance, network interruption, queued-write process-death recovery, in-flight account-switch race, release-optimized APK, physical device or 32-bit ABI runtime was verified. The AOSP API 26 image lacks Google Play Services. Fake account switching and in-memory updates do not establish live races or process-death recovery. Activity recreation is not process death. No rules/index publication or new dependency is required by these repairs.

## Exact changed-file manifest / review ZIP

The ZIP contains these **eight complete saved files**, preserving repository-relative paths and checked against source SHA-256 values:

```text
API26_VERIFICATION.md
app/src/androidTest/java/com/clarity/finance/Api26ProductionStartupTest.kt
app/src/androidTest/java/com/clarity/finance/AuthAccessibilityTest.kt
app/src/androidTest/java/com/clarity/finance/DashboardNavigationTest.kt
app/src/androidTest/java/com/clarity/finance/GoalNavigationTest.kt
app/src/main/java/com/clarity/finance/ui/auth/LoginScreen.kt
app/src/main/java/com/clarity/finance/ui/auth/RegisterScreen.kt
app/src/main/java/com/clarity/finance/ui/recurring/RecurringScreen.kt
```

Existing models, repositories, recovery storage, ViewModels, DI modules, navigation, rules, indexes, build files, dependency versions, Firebase/local configuration and unrelated files are byte-identical to the baseline. The review archive excludes SDK/AVD files, APKs/build outputs, temporary download/UI/diagnostic scripts, screenshots/logs, credentials, configuration, historical review diffs and private data. No existing test was deleted. During the runtime verification pass, no staging, commit, push or deployment was performed.

## Finalization checks

Before staging, all eight current files matched the accepted review ZIP byte-for-byte (archive SHA-256 `3B4FDF514424F220EC0381A286D789AC87B83794631DB70C8088BF90D5ABEF2F`). The seven Kotlin files remain unchanged from that ZIP. This report only corrects the plain-text page range/display dimensions and clarifies which verification phase produced the evidence. The unresolved API 35 native-crash limitation and all recorded results remain unchanged.

Checks performed for finalization are working-state backup, archive/source SHA-256 comparison, staged-diff review, whitespace/privacy checks, content-preservation checks and Git upstream/remote verification. The recorded 230 unit tests, successful app/test builds, lint 0 errors/22 warnings, final API 26 18-case run and separate production recreation test are reused; no Gradle, Android or Firestore suite was rerun for unchanged source. No device command, deployment, live write or dependency change is part of finalization.
