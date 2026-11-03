# Koin dependency-injection migration

## Requirement, baseline and choice

The original assignment brief, page 6, requires **Hilt (preferred) or Koin**, with **no manual ViewModel instantiation inside Composable functions**. The supplied requirements repeat this constraint. Koin therefore satisfies the required framework choice; Hilt is not mandatory. This completes the DI gap recorded historically in earlier milestone reports.

Baseline verified: `1a4bc186c7e93dac49ffd4b236f99dd5334f7412`. No applicable AGENTS.md was found. Before editing, all 170 tracked/nonignored saved files, staged/unstaged diffs and Git history were backed up outside the repository and source/archive hashes checked. The four unrelated IDE/review files remain unchanged and uncommitted. No commits or pushes were made.

**Koin 4.1.1** uses a runtime Kotlin DSL, avoiding a new Gradle plugin, annotation processor, KSP or kapt integration. Its [official release notes](https://github.com/InsertKoinIO/koin/releases/tag/4.1.1) record Kotlin 2.1.21; the existing project's Kotlin Compose plugin remains 2.2.10. The [official ViewModel integration](https://insert-koin.io/docs/reference/koin-core/viewmodel/) and [4.1 factory source](https://github.com/InsertKoinIO/koin/blob/4.1.1/projects/core/koin-core-viewmodel/src/commonMain/kotlin/org/koin/viewmodel/factory/KoinViewModelFactory.kt) provide AndroidX owner/extras support without implementing a new per-ViewModel factory.

The broad Android/Compose integration was evaluated but rejected: even the Compose artifact transitively brings `koin-android` and would upgrade the existing Fragment 1.1.0 dependency to 1.8.9. The final implementation instead uses only Koin core and core ViewModel support, with a small adapter to the **existing** AndroidX Compose `viewModel()` function. No unsupported dependency exclusions or forced old versions are used. There are no Koin internal/experimental API opt-ins in application code.

## Exact dependency changes and compatibility

Two direct implementation dependencies were added through the existing version catalog:

```text
io.insert-koin:koin-core:4.1.1
io.insert-koin:koin-core-viewmodel:4.1.1
```

No test dependency was needed. JUnit, Mockito, coroutine testing and Compose testing reuse their existing versions.

Resolved `debugRuntimeClasspath` snapshots show **all 191 existing module coordinates retain exactly their previous versions**. The final graph has 203 coordinates. The 12 additions, including platform/metadata variants, are:

```text
co.touchlab:stately-concurrency:2.1.0
co.touchlab:stately-concurrency-jvm:2.1.0
co.touchlab:stately-concurrent-collections:2.1.0
co.touchlab:stately-concurrent-collections-jvm:2.1.0
co.touchlab:stately-strict:2.1.0
co.touchlab:stately-strict-jvm:2.1.0
io.insert-koin:koin-core:4.1.1
io.insert-koin:koin-core-jvm:4.1.1
io.insert-koin:koin-core-viewmodel:4.1.1
io.insert-koin:koin-core-viewmodel-android:4.1.1
org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.9.5
org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-savedstate:2.9.5
```

AGP 9.3.3, Gradle 9.5.0, Kotlin Compose plugin 2.2.10, Compose BOM 2026.02.01, Firebase BOM 34.2.0 and all existing dependencies remain unchanged. The existing compile SDK 37.1, minimum SDK 26 and target SDK 36 remain unchanged; the merged manifest confirms minSdk 26/targetSdk 36. Compilation, APK packaging and Android runtime tests verify integration with this stack. **API 26 runtime acceptance remains separate and unexecuted.**

## Framework wiring and lifetimes

`ClarityApplication.onCreate()` starts one process Koin context. It loads individual platform, repository and ViewModel definitions; it does not wrap the old manual container. `MainActivity` renders navigation without constructing repositories or ViewModels. Obsolete `AppContainer.kt` and the class-switching `ViewModelFactory.kt` were removed after their production/test callers migrated.

| Dependency | Lifetime / ownership |
| --- | --- |
| Application Context, FirebaseAuth, FirebaseFirestore | Lazy process singletons in the production Koin context; unchanged default Firebase clients/target |
| DataStore<Preferences> | One process singleton referencing the **existing Context.dataStore delegate**, retaining `expense_recovery` and its existing file; no additional DataStore factory/file |
| SubmissionRecoveryStore | One process singleton shared by repository definitions |
| Auth, expense, income, payment, crypto, reporting, goal and recurring repositories | Individual lazy singleton definitions; no repository recreated on recomposition/navigation |
| Crypto, goal and recurring remotes | Individual process singletons, preserving in-flight Task tracking and account-aware current-UID closures |
| ViewModelProvider.Factory | Koin factory definition creating the library's KoinViewModelFactory for a requested ViewModel class |
| All 16 ViewModels | Koin `viewModel` definitions; AndroidX ViewModelStore controls instance reuse and clearing |

`injectedViewModel(graph)` remembers the framework factory for the graph/class and delegates to AndroidX Compose `viewModel(factory = ...)`. It contains no ViewModel constructors, repository lookups or class-specific branches. Constructor wiring belongs to Koin definitions. Navigation continues to supply the current owner and its default CreationExtras. The AuthViewModel outside NavHost retains its Activity owner; destination ViewModels retain their back-stack-entry owners. The dashboard GoalCardViewModel remains distinct from the savings screen GoalViewModel and keeps its existing observation-only behavior.

Navigation arguments and screen load calls are unchanged. Existing production ViewModels do not accept SavedStateHandle; the new Android regression demonstrates that the selected framework factory nonetheless receives real navigation CreationExtras, including a genuinely null optional milestone argument. Recomposition reuses the same owner, another destination receives a distinct ViewModel, popping clears it, and returning reuses the prior entry's instance.

DI startup does not start recovery workers. Existing ViewModel-owned synchronization, listener cleanup, CancellationException propagation and account/target checks remain unchanged. All business ViewModel, repository, model, money and recovery-store source files remain byte-identical to baseline. Rules, indexes, Firebase configuration and financial schemas were not changed.

## Isolated test injection

The five existing navigation test classes now use `isolatedAppGraph` and the **same production ViewModel definitions**. Each creates a standalone `koinApplication`, not a global override. It loads only the ViewModel module plus fake repository bindings; platform/production repository modules are absent. Every destination receives this graph explicitly through AppNavigation, preventing fallback to the Application's production context.

Unused fake repositories fail immediately on unexpected calls. The dashboard's default fake goal observer returns an empty known snapshot and rejects actions; it cannot instantiate a live repository. A DisposableEffect closes only the isolated graph when its composition is disposed. AndroidX owners remain responsible for clearing ViewModels.

The new unit tests verify production singleton definitions with mocked platform objects and no remote work, all 16 ViewModel definitions as factories with signed-out fake interfaces, and failure of a missing isolated binding even when a global binding exists. The Android ownership test uses only an isolated probe module and the production factory adapter. No live audit/writer class was selected.

## Actual verification — 3 November 2026

Focused graph/integration command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*AppModulesTest' :app:compileDebugAndroidTestKotlin :app:diDependencySnapshot -I build/di-dependencies.init.gradle '-PdiSnapshot=after' --console=plain
```

Result: **3 focused unit tests passed**, production/test compilation succeeded, and resolved dependency comparison found no existing version change. The ignored snapshot init script only inspects dependency resolution; it is not required to build the application.

Full required command:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
```

Result: **BUILD SUCCESSFUL**, **230 unit tests**, zero failures/errors/skips, both APK builds successful, lint **0 errors / 22 warnings**. This retains the previous 227 unit tests and adds three. The two additional lint warnings advertise newer Koin versions; the compatible version remains intentionally pinned. Existing warning categories were not suppressed and unrelated dependencies were not upgraded.

Storage-preserving installations and focused owner check:

```powershell
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -e class com.clarity.finance.DiViewModelOwnershipTest com.clarity.finance.test/androidx.test.runner.AndroidJUnitRunner
```

Result: **OK (1 test)** on the existing API 37 emulator: navigation ownership, recomposition reuse, clearing and typed SavedStateHandle arguments all passed.

Existing isolated Android navigation/feedback command:

```powershell
adb -s emulator-5554 shell am instrument -w -e class com.clarity.finance.PaymentNavigationTest,com.clarity.finance.CryptoNavigationTest,com.clarity.finance.CryptoFeedbackUiTest,com.clarity.finance.DashboardNavigationTest,com.clarity.finance.GoalNavigationTest,com.clarity.finance.RecurringNavigationTest com.clarity.finance.test/androidx.test.runner.AndroidJUnitRunner
```

The initial execution ran **15 tests: 14 passed and one failed** because the migration script accidentally changed the recurring test's en dash in a selector. The exact original UTF-8 selector was restored; no assertion was removed or weakened. Then the current test APK was rebuilt and only that affected case rerun:

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest --console=plain
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -e class 'com.clarity.finance.RecurringNavigationTest#templateEditingAndCancellationKeepStopDueCandidatesAndRetainActualHistory' com.clarity.finance.test/androidx.test.runner.AndroidJUnitRunner
```

Result: rebuild successful; **OK (1 test)**. All 15 existing cases therefore have passing executions, plus the separate new ownership case: **16 unique Android cases verified**, with the initial selector failure explicitly retained in the evidence. Unchanged successful cases and full unit/build/lint results were reused after the test-only text correction; the entire unchanged suite was not repeatedly restarted. No test hung.

`git diff --check` passed. Final scope/hash checks verify unchanged business code, rules/indexes, the four unrelated files and baseline HEAD. The test device's recovery protobuf was read privately before/after and remained **byte-for-byte identical**. No emulator storage was cleared, no package was uninstalled, and no device networking or DNS configuration command was issued.

The previous **124 passing Firestore emulator tests** are reused evidence because rules and Firestore data-access logic are unchanged. They were not rerun for DI. No live financial read/write acceptance or recovery retry was performed. API 26 runtime testing and independent process-death/rotation acceptance were not performed; they are not inferred from compilation or the focused navigation test.

## Exact current files in the review ZIP

The ZIP contains these **16 complete current saved files**, preserving relative paths, verified against source SHA-256 values. The two intentional removals are listed below rather than including obsolete copies. Credentials, Firebase/local configuration, build outputs, dependency caches, private evidence, historical review diffs and IDE settings are excluded. The external ZIP path/hash are reported separately after packaging.

```text
DI_MIGRATION.md
app/build.gradle.kts
app/src/androidTest/java/com/clarity/finance/CryptoNavigationTest.kt
app/src/androidTest/java/com/clarity/finance/DashboardNavigationTest.kt
app/src/androidTest/java/com/clarity/finance/DiViewModelOwnershipTest.kt
app/src/androidTest/java/com/clarity/finance/GoalNavigationTest.kt
app/src/androidTest/java/com/clarity/finance/IsolatedAppGraph.kt
app/src/androidTest/java/com/clarity/finance/PaymentNavigationTest.kt
app/src/androidTest/java/com/clarity/finance/RecurringNavigationTest.kt
app/src/main/java/com/clarity/finance/ClarityApplication.kt
app/src/main/java/com/clarity/finance/MainActivity.kt
app/src/main/java/com/clarity/finance/di/AppModules.kt
app/src/main/java/com/clarity/finance/di/InjectedViewModel.kt
app/src/main/java/com/clarity/finance/ui/navigation/AppNavigation.kt
app/src/test/java/com/clarity/finance/di/AppModulesTest.kt
gradle/libs.versions.toml
```

Intentional obsolete-file removals:

```text
app/src/main/java/com/clarity/finance/di/AppContainer.kt
app/src/main/java/com/clarity/finance/di/ViewModelFactory.kt
```

This is a DI-only migration. It adds no financial feature, schema, security policy, recovery format or calculation, and performs no commit, push, deployment, live-data mutation or fixture cleanup. The historical Hilt/Koin compliance gap is addressed; the separate API 26 acceptance milestone remains outstanding.
