Status: Complete
Commits: `feat: add login screen and session routing` (this commit)
RED: `LoginViewModelTest` failed compilation with unresolved `LoginViewModel`, `LoginEvent`, and `SessionDestination`.
GREEN: Focused LoginViewModel tests passed; full `testDebugUnitTest` passed.
Tests: `./gradlew testDebugUnitTest`; `./gradlew lintDebug assembleDebug`.
Concerns: Home is intentionally a placeholder for Task 6; local Gradle CLI needs Android Studio JBR 17 via `JAVA_HOME`.
Report: `.superpowers/sdd/task-5-report.md`
