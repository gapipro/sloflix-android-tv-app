Status: Complete
Commits: `feat: add login screen and session routing` (this commit)
RED: `LoginViewModelTest` failed compilation with unresolved `LoginViewModel`, `LoginEvent`, and `SessionDestination`.
GREEN: Focused LoginViewModel tests passed; full `testDebugUnitTest` passed.
Tests: `./gradlew testDebugUnitTest`; `./gradlew lintDebug assembleDebug`.
Concerns: Home is intentionally a placeholder for Task 6; local Gradle CLI needs Android Studio JBR 17 via `JAVA_HOME`.
Report: `.superpowers/sdd/task-5-report.md`

## Review fix: Compose call-order in SloflixNav

Status: Complete
Commit: `fix: hoist nav controller into SloflixNavContent`
Finding: `rememberNavController()` and event `LaunchedEffect` were called after early return on `SessionDestination.Checking`, violating Compose call-order rules.
Fix: Extracted post-check navigation into `SloflixNavContent`; parent uses if/else without early return.
Tests: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests "com.sloflix.tv.ui.login.LoginViewModelTest"` — BUILD SUCCESSFUL. No nav-specific unit tests found.
