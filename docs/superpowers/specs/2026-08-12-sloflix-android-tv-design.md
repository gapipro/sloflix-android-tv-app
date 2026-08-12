# Sloflix Android TV App — Design Spec

**Date:** 2026-08-12  
**Status:** Approved for planning (pending user review of this document)  
**Target device:** Nvidia Shield, Android 11 (API 30)

## Goal

Build a native Android TV application that consumes the live Sloflix backend (same APIs as https://www.sloflix.com/) and supports:

- Categories
- Filtering
- Login
- Video details
- Video playback
- Remember login across app restarts
- Continue watching / resume playback when the API supports it

Visual direction: dark cinematic TV UI inspired by the site, not a pixel-perfect web clone.

## Non-goals (v1)

- Play Store publishing
- Multi-user profiles
- Downloads / offline viewing
- Pixel-perfect parity with the website
- Standalone search (unless filtering on the site is search-like and maps cleanly)

## Architecture

Single Android application module with layered packages:

| Layer | Responsibility |
|-------|----------------|
| `ui` | Compose for TV screens, navigation, focus, theme |
| `domain` | Models and use cases (login, browse, filter, details, resume) |
| `data` | Retrofit/OkHttp API, DTOs, repositories, DataStore session |

**Stack**

- Kotlin
- Jetpack Compose for TV
- Navigation (Compose)
- OkHttp + Retrofit
- Media3 ExoPlayer
- DataStore for session persistence
- minSdk 30, targetSdk current stable

**Base URL:** discovered from the live site (likely `https://www.sloflix.com` or an API subdomain). Implemented behind a thin `SloflixApi` interface so endpoint details can change without rewriting UI.

**Credentials:** never hardcoded in source. Users sign in on the Login screen. Test accounts are provided out-of-band for manual QA only.

## Screens & navigation

```
Login → Home ⇄ Filters (overlay/panel)
         ↓
       Details → Player
```

1. **Login** — username/password, TV-friendly input, loading and error states.
2. **Home** — category rows of posters; optional Continue Watching row when API supports it.
3. **Filters** — side panel / overlay on Home; options mirror what the website exposes (discovered during API reverse-engineering).
4. **Details** — title art, description, metadata, Play and Resume (when progress exists).
5. **Player** — full-screen Media3 controls suitable for D-pad; Back returns to Details.

**Shared UI:** focused poster cards, loading/empty/error states, dark theme with restrained brand accent once branding from the site is known.

## Data flow

1. **Cold start** — read session from DataStore; if valid → Home, else → Login.
2. **Login** — authenticate → persist session → Home.
3. **Home** — load categories/items (+ continue watching if available).
4. **Filter** — apply filters → refetch or client-filter per API capabilities → update Home.
5. **Details** — load item by id.
6. **Playback** — resolve stream URL with required auth → play → report progress when endpoint exists → refresh continue watching on exit.

### API discovery (prerequisite)

Reverse-engineer live site traffic to document:

- Auth mechanism (token, cookie, CSRF, etc.)
- Category / catalog listing
- Filter parameters
- Title details payload
- Stream URL resolution
- Progress / continue-watching read & write (optional; feature gated if missing)

Document findings in `docs/superpowers/specs/` (or an adjacent API notes file) before locking DTO shapes.

Cloudflare or bot protection may require browser-assisted discovery; the Android client should send a normal mobile/TV user-agent and reuse the same auth scheme the SPA uses after login.

## Error handling

| Case | Behavior |
|------|----------|
| Bad credentials | Stay on Login with clear error |
| Expired session | Clear DataStore → Login with short message |
| Browse/details failure | On-screen retry; keep stale content if present |
| Empty filters | Empty state + clear-filters action |
| Playback failure | Overlay error → Back to Details |
| Offline | Explicit offline message (no infinite spinner) |
| Missing optional APIs | Hide Continue Watching / Resume; do not block core flow |

## Testing & delivery

**Automated**

- Unit tests for session parsing and catalog/DTO mapping with fake responses.

**Manual**

- Emulator smoke: Login → Home → Details → Player (sample stream if CDN blocks emulator).
- Nvidia Shield via **adb** (user-provided): focus navigation, session persist across restart, filter, play, resume if available.

**Delivery**

- Sideloaded debug/release APK.
- README with Gradle build, `adb install`, and login instructions (no secrets in repo).

## Security notes

- Do not commit passwords, tokens, or HAR files containing secrets.
- Persist session in app-private DataStore only (no credentials stored—session/token after login). Do not log tokens in release builds.
- Strip auth headers from crash logs and debug output in release builds.

## Success criteria

- Sideload and launch on Nvidia Shield (Android 11) from the TV launcher.
- Sign in with a real Sloflix account; stay signed in after process death / restart.
- Browse categories, apply filters, open details, play video with D-pad.
- If progress API exists: resume from details and show Continue Watching on Home.
