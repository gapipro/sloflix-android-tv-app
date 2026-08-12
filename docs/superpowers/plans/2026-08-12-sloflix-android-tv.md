# Sloflix Android TV Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a sideloadable Android TV app for Nvidia Shield (Android 11) that logs into Sloflix, browses categories with filters, shows title details, plays video, persists session, and supports continue-watching when the API allows.

**Architecture:** Single-module Kotlin app with `ui` / `domain` / `data` packages. Compose for TV for D-pad UI, Retrofit/OkHttp for the reverse-engineered Sloflix API, DataStore for session, Media3 ExoPlayer for playback. Domain repository interfaces stay stable; DTOs and Retrofit paths are filled from API discovery.

**Tech Stack:** Kotlin, Jetpack Compose for TV, Navigation Compose, OkHttp, Retrofit, Kotlinx Serialization, DataStore Preferences, Media3 ExoPlayer, JUnit4, Robolectric/JVM unit tests, Gradle Kotlin DSL, minSdk 30.

## Global Constraints

- minSdk 30 (Android 11 / Shield); TV leanback launcher activity
- Never hardcode Sloflix credentials or tokens in source, tests fixtures may use fake tokens only
- Do not commit HAR files or cookies containing real secrets
- Dark cinematic TV UI (not pixel-perfect web clone)
- Optional continue-watching / resume: feature-gate if API lacks endpoints
- Spec: `docs/superpowers/specs/2026-08-12-sloflix-android-tv-design.md`

---

## File structure (locked in)

```
sloflix/
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  gradle/libs.versions.toml
  README.md
  app/
    build.gradle.kts
    proguard-rules.pro
    src/main/AndroidManifest.xml
    src/main/java/com/sloflix/tv/
      SloflixApp.kt
      MainActivity.kt
      di/AppContainer.kt
      domain/model/
        Session.kt
        Category.kt
        TitleSummary.kt
        TitleDetails.kt
        StreamInfo.kt
        FilterState.kt
        PlaybackProgress.kt
      domain/repo/
        AuthRepository.kt
        CatalogRepository.kt
        PlaybackRepository.kt
      domain/session/SessionStore.kt
      data/session/DataStoreSessionStore.kt
      data/api/
        SloflixApi.kt
        dto/*.kt
        AuthInterceptor.kt
        NetworkModule.kt
      data/repo/
        AuthRepositoryImpl.kt
        CatalogRepositoryImpl.kt
        PlaybackRepositoryImpl.kt
      ui/theme/Theme.kt
      ui/nav/SloflixNav.kt
      ui/login/LoginScreen.kt
      ui/home/HomeScreen.kt
      ui/home/FilterPanel.kt
      ui/details/DetailsScreen.kt
      ui/player/PlayerScreen.kt
      ui/components/PosterCard.kt
      ui/components/UiState.kt
    src/test/java/com/sloflix/tv/
      domain/...
      data/...
  docs/superpowers/specs/
    2026-08-12-sloflix-android-tv-design.md
    2026-08-12-sloflix-api.md          # produced in Task 2
```

---

### Task 1: Android TV project scaffold

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/sloflix/tv/SloflixApp.kt`, `app/src/main/java/com/sloflix/tv/MainActivity.kt`, `app/src/main/java/com/sloflix/tv/ui/theme/Theme.kt`, `README.md` (stub)
- Test: verify assemble with Gradle

**Interfaces:**
- Consumes: none
- Produces: installable empty TV app package `com.sloflix.tv`, `MainActivity` as LEANBACK_LAUNCHER

- [ ] **Step 1: Create Gradle version catalog**

Create `gradle/libs.versions.toml`:

```toml
[versions]
agp = "8.7.3"
kotlin = "2.0.21"
compose-bom = "2024.10.01"
tv-foundation = "1.0.0-alpha11"
tv-material = "1.0.0"
activity-compose = "1.9.3"
navigation = "2.8.4"
lifecycle = "2.8.7"
okhttp = "4.12.0"
retrofit = "2.11.0"
serialization = "1.7.3"
retrofit-serialization = "1.0.0"
datastore = "1.1.1"
media3 = "1.4.1"
coroutines = "1.9.0"
junit = "4.13.2"
robolectric = "4.13"

[libraries]
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activity-compose" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-tv-foundation = { module = "androidx.tv:tv-foundation", version.ref = "tv-foundation" }
androidx-tv-material = { module = "androidx.tv:tv-material", version.ref = "tv-material" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-logging = { module = "com.squareup.okhttp3:logging-interceptor", version.ref = "okhttp" }
retrofit = { module = "com.squareup.retrofit2:retrofit", version.ref = "retrofit" }
retrofit-kotlinx-serialization = { module = "com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter", version.ref = "retrofit-serialization" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
media3-exoplayer = { module = "androidx.media3:media3-exoplayer", version.ref = "media3" }
media3-ui = { module = "androidx.media3:media3-ui", version.ref = "media3" }
media3-okhttp = { module = "androidx.media3:media3-datasource-okhttp", version.ref = "media3" }
coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 2: Create root Gradle files**

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "sloflix"
include(":app")
```

`build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```

`gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 3: Create `app/build.gradle.kts` and manifest**

`app/build.gradle.kts` must set `namespace = "com.sloflix.tv"`, `compileSdk = 35`, `minSdk = 30`, `targetSdk = 35`, enable Compose, add TV + networking + Media3 deps from the catalog.

`AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-feature android:name="android.software.leanback" android:required="true" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />

    <application
        android:name=".SloflixApp"
        android:allowBackup="false"
        android:banner="@drawable/ic_banner"
        android:icon="@mipmap/ic_launcher"
        android:label="Sloflix"
        android:supportsRtl="true"
        android:theme="@style/Theme.Sloflix"
        android:usesCleartextTraffic="false">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="landscape"
            android:configChanges="keyboard|keyboardHidden|navigation">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

Add minimal `res` assets: `values/themes.xml`, vector banner/launcher placeholders, `mipmap` or adaptive icon.

- [ ] **Step 4: Empty Compose TV entry**

`MainActivity.kt` hosts `setContent { SloflixTheme { Text("Sloflix") } }` using `androidx.tv.material3`.

- [ ] **Step 5: Assemble**

Run: `./gradlew :app:assembleDebug`  
Expected: `BUILD SUCCESSFUL` and APK at `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle/libs.versions.toml app README.md
git commit -m "chore: scaffold Android TV app module"
```

---

### Task 2: Reverse-engineer Sloflix API and document contracts

**Files:**
- Create: `docs/superpowers/specs/2026-08-12-sloflix-api.md`
- Create (optional scratch, gitignored): `.local/sloflix-discovery/` for HAR redacted notes

**Interfaces:**
- Consumes: live site https://www.sloflix.com/ ; credentials from user (env `SLOFLIX_USER` / `SLOFLIX_PASS`, never commit)
- Produces: API doc with exact auth scheme, endpoints, sample JSON (redacted), and mapping to domain methods below

Required domain methods the API must support (or document as unavailable):

```kotlin
// AuthRepository
suspend fun login(username: String, password: String): Result<Session>
suspend fun validateSession(session: Session): Boolean

// CatalogRepository
suspend fun categories(session: Session): Result<List<Category>>
suspend fun titles(session: Session, categoryId: String?, filter: FilterState): Result<List<TitleSummary>>
suspend fun filterOptions(session: Session): Result<FilterState> // available option lists embedded in FilterState
suspend fun details(session: Session, titleId: String): Result<TitleDetails>
suspend fun continueWatching(session: Session): Result<List<TitleSummary>> // empty/failure → feature off

// PlaybackRepository
suspend fun stream(session: Session, titleId: String): Result<StreamInfo>
suspend fun saveProgress(session: Session, progress: PlaybackProgress): Result<Unit>
suspend fun loadProgress(session: Session, titleId: String): Result<PlaybackProgress?>
```

- [ ] **Step 1: Discover auth**

Using a real browser (Cloudflare may block raw curl):

1. Open https://www.sloflix.com/
2. Login with credentials from the user (do not write them into the repo)
3. In DevTools Network, capture the login request (method, URL, body, response headers/cookies/token)
4. Note how subsequent XHR calls authenticate (Cookie, Authorization bearer, custom header)

- [ ] **Step 2: Discover catalog, filter, details, stream, progress**

Exercise: home categories, change filters, open a title, press play, scrub/pause if progress saves.

For each call record in the API doc:

- Method + path
- Query/body params
- Auth requirement
- Redacted JSON example
- Maps to which repository method

- [ ] **Step 3: Write `docs/superpowers/specs/2026-08-12-sloflix-api.md`**

Must include sections: Base URL, Auth, Categories, Titles/Filter, Details, Stream, Progress/ContinueWatching (or “Not available”), Known Cloudflare/CORS notes for Android OkHttp.

- [ ] **Step 4: Commit API doc only (no secrets)**

```bash
git add docs/superpowers/specs/2026-08-12-sloflix-api.md
git commit -m "docs: document reverse-engineered Sloflix API"
```

---

### Task 3: Domain models + SessionStore (TDD)

**Files:**
- Create: `app/src/main/java/com/sloflix/tv/domain/model/*.kt`, `app/src/main/java/com/sloflix/tv/domain/session/SessionStore.kt`, `app/src/main/java/com/sloflix/tv/data/session/DataStoreSessionStore.kt`
- Test: `app/src/test/java/com/sloflix/tv/data/session/DataStoreSessionStoreTest.kt`

**Interfaces:**
- Consumes: none
- Produces:

```kotlin
data class Session(val accessToken: String, val cookieHeader: String? = null)

interface SessionStore {
    suspend fun get(): Session?
    suspend fun set(session: Session)
    suspend fun clear()
}
```

- [ ] **Step 1: Write failing test**

```kotlin
@RunWith(RobolectricTestRunner::class)
class DataStoreSessionStoreTest {
    @Test
    fun roundTripAndClear() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = DataStoreSessionStore(context)
        assertNull(store.get())
        store.set(Session(accessToken = "tok", cookieHeader = "a=b"))
        assertEquals(Session("tok", "a=b"), store.get())
        store.clear()
        assertNull(store.get())
    }
}
```

- [ ] **Step 2: Run test — expect FAIL (class missing)**

Run: `./gradlew :app:testDebugUnitTest --tests com.sloflix.tv.data.session.DataStoreSessionStoreTest`  
Expected: FAIL compilation or class not found

- [ ] **Step 3: Implement models + DataStoreSessionStore**

Use Preferences DataStore keys `access_token`, `cookie_header`. Empty token means no session.

Also add immutable domain models:

```kotlin
data class Category(val id: String, val name: String)
data class TitleSummary(
    val id: String,
    val name: String,
    val posterUrl: String?,
    val progressFraction: Float? = null,
)
data class TitleDetails(
    val id: String,
    val name: String,
    val description: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: Int?,
    val genres: List<String>,
    val resumePositionMs: Long?,
)
data class StreamInfo(val url: String, val headers: Map<String, String> = emptyMap())
data class FilterState(
    val selectedGenreIds: Set<String> = emptySet(),
    val selectedYear: Int? = null,
    val query: String? = null,
    val availableGenres: List<Pair<String, String>> = emptyList(), // id to label
    val availableYears: List<Int> = emptyList(),
)
data class PlaybackProgress(val titleId: String, val positionMs: Long, val durationMs: Long)
```

Adjust field availability later only if API doc proves a field never exists; keep types.

- [ ] **Step 4: Re-run tests — expect PASS**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sloflix/tv/domain app/src/main/java/com/sloflix/tv/data/session app/src/test
git commit -m "feat: add domain models and DataStore session store"
```

---

### Task 4: Network module + repository implementations

**Files:**
- Create: `data/api/*`, `data/repo/*`, `domain/repo/*`, `di/AppContainer.kt`
- Test: `app/src/test/java/com/sloflix/tv/data/repo/AuthRepositoryImplTest.kt` (MockWebServer)

**Interfaces:**
- Consumes: `2026-08-12-sloflix-api.md`, `Session`, `SessionStore`
- Produces: `AuthRepository`, `CatalogRepository`, `PlaybackRepository` implementations wired in `AppContainer`

```kotlin
interface AuthRepository {
    suspend fun login(username: String, password: String): Result<Session>
    suspend fun validateSession(session: Session): Boolean
}
interface CatalogRepository {
    suspend fun categories(session: Session): Result<List<Category>>
    suspend fun titles(session: Session, categoryId: String?, filter: FilterState): Result<List<TitleSummary>>
    suspend fun filterOptions(session: Session): Result<FilterState>
    suspend fun details(session: Session, titleId: String): Result<TitleDetails>
    suspend fun continueWatching(session: Session): Result<List<TitleSummary>>
}
interface PlaybackRepository {
    suspend fun stream(session: Session, titleId: String): Result<StreamInfo>
    suspend fun saveProgress(session: Session, progress: PlaybackProgress): Result<Unit>
    suspend fun loadProgress(session: Session, titleId: String): Result<PlaybackProgress?>
}
```

- [ ] **Step 1: Write failing MockWebServer login test**

Enqueue a canned response matching the API doc login success. Assert `AuthRepositoryImpl.login` returns `Session` with expected token/cookie.

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Implement OkHttp + Retrofit**

- `AuthInterceptor` attaches `Session` from a `AtomicReference`/`SessionProvider` set by repositories after login
- `SloflixApi` Retrofit interface paths **copied from API doc**
- Map DTOs → domain models in repository impls
- If continue-watching/progress endpoints are “Not available” in API doc, implement methods returning `Result.success(emptyList())` / `Result.success(null)` / no-op success

- [ ] **Step 4: Tests PASS**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: add Sloflix API client and repositories"
```

---

### Task 5: Login screen + session routing

**Files:**
- Create: `ui/login/LoginScreen.kt`, `ui/login/LoginViewModel.kt`, `ui/nav/SloflixNav.kt`
- Modify: `MainActivity.kt`, `SloflixApp.kt`
- Test: `LoginViewModelTest.kt` with fake `AuthRepository` / `SessionStore`

**Interfaces:**
- Consumes: `AuthRepository.login`, `SessionStore`
- Produces: navigation start destination `login` or `home` based on stored session

- [ ] **Step 1: Failing ViewModel test** — successful login writes session and emits NavigateHome; bad password emits error message string

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement LoginViewModel + LoginScreen (Compose for TV)**

- Username/password fields, Submit button with focus order
- Show error text on failure
- On success: `sessionStore.set` + navigate to home
- Cold start: if `sessionStore.get()` non-null and `validateSession` true → home; if validate false → `clear()` → login with “Session expired”

- [ ] **Step 4: Tests PASS**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: add login screen and session routing"
```

---

### Task 6: Home categories UI

**Files:**
- Create: `ui/home/HomeScreen.kt`, `ui/home/HomeViewModel.kt`, `ui/components/PosterCard.kt`, `ui/components/UiState.kt`
- Test: `HomeViewModelTest.kt`

**Interfaces:**
- Consumes: `CatalogRepository.categories`, `titles`, `continueWatching`
- Produces: Home rows; click title → navigate `details/{id}`

```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Ready<T>(val value: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
```

- [ ] **Step 1: Failing HomeViewModel test** — fake catalog returns 2 categories; state becomes Ready with rows

- [ ] **Step 2: Implement HomeViewModel + HomeScreen**

- Dark background
- Vertical list of horizontal TV lazy rows
- Optional first row “Continue watching” only when `continueWatching` returns non-empty
- Error state with Retry
- Focus-friendly `PosterCard`

- [ ] **Step 3: Tests PASS + commit**

```bash
git commit -m "feat: add home category rows"
```

---

### Task 7: Filter panel

**Files:**
- Create: `ui/home/FilterPanel.kt`
- Modify: `HomeViewModel.kt`, `HomeScreen.kt`
- Test: `HomeViewModelFilterTest.kt`

**Interfaces:**
- Consumes: `CatalogRepository.filterOptions`, `titles(..., filter)`
- Produces: applied `FilterState` refreshes home titles

- [ ] **Step 1: Failing test** — selecting a genre updates filter and reloads titles via fake repo

- [ ] **Step 2: Implement FilterPanel** as side overlay opened from Home (button “Filters”)

- Show available genres/years from `filterOptions` (fields unused if API returns empty lists)
- Clear filters action
- Empty results → “No titles match” + clear action

- [ ] **Step 3: Tests PASS + commit**

```bash
git commit -m "feat: add catalog filtering on home"
```

---

### Task 8: Details screen

**Files:**
- Create: `ui/details/DetailsScreen.kt`, `ui/details/DetailsViewModel.kt`
- Test: `DetailsViewModelTest.kt`

**Interfaces:**
- Consumes: `CatalogRepository.details`, `PlaybackRepository.loadProgress`
- Produces: Play → `player/{id}`; Resume if `resumePositionMs` or loaded progress > 0

- [ ] **Step 1: Failing test** — details Ready shows resume CTA when progress > 0

- [ ] **Step 2: Implement details UI** — backdrop/poster, title, year, genres, description, Play/Resume, error/retry

- [ ] **Step 3: Tests PASS + commit**

```bash
git commit -m "feat: add title details screen"
```

---

### Task 9: Player + progress reporting

**Files:**
- Create: `ui/player/PlayerScreen.kt`, `ui/player/PlayerViewModel.kt`
- Test: `PlayerViewModelTest.kt` (progress save interval logic with fake clock/repo)

**Interfaces:**
- Consumes: `PlaybackRepository.stream`, `saveProgress`
- Produces: full-screen playback; Back → details

- [ ] **Step 1: Failing test** — every 15s of position advance calls `saveProgress` when duration known

- [ ] **Step 2: Implement PlayerScreen with Media3**

- Build `MediaItem` from `StreamInfo.url`
- Attach `StreamInfo.headers` via OkHttp DataSource factory when headers non-empty
- Start at resume position if navigated with `startPositionMs`
- On error: overlay “Can’t play this title” + Back
- On dispose / Back: final `saveProgress`

- [ ] **Step 3: Tests PASS + commit**

```bash
git commit -m "feat: add ExoPlayer playback and progress save"
```

---

### Task 10: Wiring polish, offline errors, README, Shield smoke

**Files:**
- Modify: theme colors to cinematic dark + brand accent sampled from site
- Modify: repositories to map `UnknownHostException` / no-network to user-facing offline messages
- Modify: `README.md` with build/install/login (no passwords)
- Test: manual on Shield via adb

**Interfaces:**
- Consumes: full app
- Produces: documented sideload path + verified Shield checklist

- [ ] **Step 1: README**

```markdown
# Sloflix Android TV

## Build
./gradlew :app:assembleDebug

## Install on Shield
adb connect <shield-ip>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk

## Sign in
Open Sloflix from the TV launcher and sign in with your Sloflix account.
```

- [ ] **Step 2: Map network failures** to “You’re offline” in ViewModels (catch IOExceptions)

- [ ] **Step 3: Shield manual checklist (user adb)**

1. App appears in Android TV launcher  
2. Login success / failure messages  
3. Kill app → relaunch still logged in  
4. Categories scroll with D-pad  
5. Filters change results  
6. Details → Play works  
7. If progress API exists: Resume + Continue watching row  

- [ ] **Step 4: Commit**

```bash
git commit -m "docs: add README and polish offline/error UX"
```

---

## Self-review (plan vs spec)

| Spec requirement | Task |
|------------------|------|
| Categories | 6 |
| Filtering | 7 |
| Login | 5 |
| Video details | 8 |
| Video playback | 9 |
| Remember login | 3, 5 |
| Continue watching / resume | 2 (discover), 4 (gate), 6, 8, 9 |
| Compose for TV single module | 1 |
| Reverse-engineer API | 2 |
| No hardcoded credentials | Global + 2, 5, 10 |
| Error handling table | 5–9, 10 |
| Unit tests for mapping/session | 3, 4 |
| Shield adb manual test | 10 |
| Sideload APK + README | 1, 10 |

No intentional TBD steps; Retrofit paths are defined by Task 2’s committed API doc before Task 4 proceeds.
