# Sloflix API contract

Verified against the live production SPA and API on 2026-08-12. Examples are reduced and redact user data, bearer tokens, signed media URLs, and subtitle locations.

## Base URL

- Production API: `https://api.sloflix.com/v1`
- Web client: `https://www.sloflix.com`
- Player: `https://player.sloflix.com`
- JSON request header: `Content-Type: application/json`
- Common response envelope: `code` (number), `status` (`"success"` or `"failed"`), plus `data`, `metadata`, or `message` depending on the endpoint.

The catalog and genre endpoints are public. Details, playback sources, user preferences, and progress require a session.

## Auth

### Login

`POST /user/login`

Body:

```json
{
  "username": "<username>",
  "password": "<password>"
}
```

Successful response:

```json
{
  "code": 200,
  "message": "<server message>",
  "metadata": {
    "access_token": "<redacted-jwt>"
  },
  "status": "success"
}
```

The access token is a three-segment JWT. Observed claim keys are `data`, `iat`, `exp`, `aud`, `iss`, and `sub`. The SPA stores it as `authToken` in local storage. Send it on authenticated requests as:

```http
Authorization: Bearer <redacted-jwt>
```

Mapping: `AuthRepository.login(username, password)`.

### Session validation

The SPA first decodes the JWT locally and rejects an expired `exp`. It then verifies the session through:

`GET /user/preferences/`

Successful response:

```json
{
  "code": 200,
  "status": "success",
  "data": {
    "user_id": "<redacted>",
    "email": "<redacted>",
    "username": "<redacted>",
    "permission": "default",
    "email_notifications": 1,
    "dark_mode": 1,
    "language": "sl",
    "streamp2p_folder": null,
    "comment_count": 0,
    "suggestion_count": 0,
    "suggestion_upvote_count": 0,
    "rating_count": 0,
    "favourite_count": 0
  }
}
```

Mapping: `AuthRepository.validateSession(session)`. Check JWT expiry first, then require HTTP 200 with `status == "success"` from this endpoint. The API was observed returning HTTP 500 with `status == "failed"` for a missing bearer token, not a conventional 401.

## Categories

There is no separate home-category endpoint. Genres are the only server-provided category taxonomy:

`GET /genre`

Auth: not required.

Response:

```json
{
  "code": 200,
  "data": [
    {
      "genre_name": "Akcija",
      "genre_id": 1
    },
    {
      "genre_name": "Komedija",
      "genre_id": 11
    }
  ],
  "status": "success"
}
```

Mapping: `CatalogRepository.categories(session)`. Represent each genre as a category; the `session` argument is not needed by this endpoint.

## Titles and filters

`GET /media`

Auth: not required. A bearer token may be sent; authenticated responses populate user-specific fields such as `has_watched`.

Query parameters:

- `sortBy`: integer sort ID.
- `genres`: comma-separated genre IDs, or empty.
- `type`: `1` for films, `2` for series, or empty.
- `query`: free-text search, or empty.
- `limit`: page size; the SPA uses `100`.
- `offset`: zero-based result offset.

Example:

```http
GET /media?sortBy=1&genres=1,11&type=1&query=&limit=100&offset=0
```

Reduced response:

```json
{
  "metadata": {
    "all_movies": 1965
  },
  "data": [
    {
      "media_year": 1998,
      "created_at": "2026-08-10 22:31:49",
      "media_type": 1,
      "media_name": "Nenavaden par 2",
      "media_name_en": "The Odd Couple II",
      "media_rating": null,
      "media_id": 27610,
      "media_genres": [
        "Pustolovščina",
        "Komedija"
      ],
      "media_thumbnail_url": "<redacted-url>",
      "media_banner_url": "<redacted-url>",
      "has_watched": false
    }
  ],
  "status": "success",
  "code": 200
}
```

Mapping: `CatalogRepository.titles(session, categoryId, filter)`. Put `categoryId` in `genres`; combine it with any selected genre IDs. Map `FilterState` search, media type, sort, limit, and offset to the corresponding parameters.

### Filter options

Genre choices come from `GET /genre`. Type and sort choices are client constants in the production SPA:

- Types: `1` Films, `2` Series.
- Sorts: `1` newest added, `2` oldest added, `3` highest rating, `4` year descending, `5` year ascending, `6` most watched, `7` relevance.
- Default sort: `7` when `query` is non-empty; otherwise `1`.

Mapping: `CatalogRepository.filterOptions(session)`. Merge the server genre list with these static type and sort lists.

## Details

`GET /media/single/{titleId}`

Auth: required.

Optional query parameters:

- `dont_count_view=true`: do not increment the title's view count. Use this for repository details and progress reads.
- `use_legacy_player=true`: request legacy-player source handling. Not needed for the current player.

Reduced response:

```json
{
  "code": 200,
  "data": {
    "media_year": 1998,
    "created_at": "2026-08-10 22:31:49",
    "media_type": 1,
    "media_length": "1:36:18",
    "season": null,
    "media_thumbnail_url": "<redacted-url>",
    "media_banner_url": "<redacted-url>",
    "parent_media_id": null,
    "episode_index": null,
    "media_name": "Nenavaden par 2",
    "media_name_en": "The Odd Couple II",
    "user_rating": null,
    "media_description": "<description>",
    "media_id": 27610,
    "is_favourite": 0,
    "seasons": [],
    "media_genres": [
      {
        "genre_name": "Pustolovščina",
        "genre_id": 2
      }
    ],
    "media_sources": [
      {
        "media_source": "<redacted-signed-player-url>",
        "media_source_name": "SLOSubs (DoodStream)",
        "dood_file_code": null,
        "subtitle_location": "<redacted>",
        "refresh_subtitles": 1
      }
    ],
    "media_rating": null,
    "metadata": {
      "watch_time": 0
    }
  },
  "status": "success"
}
```

For series and episodes, `seasons`, `season`, `episode_index`, and `parent_media_id` carry navigation data. `media_rating` is nullable and may be an aggregate object in detail responses; do not model it as the catalog list's nullable string only.

Mapping: `CatalogRepository.details(session, titleId)`.

## Stream

There is no separate stream endpoint. Extract `data.media_sources` from:

`GET /media/single/{titleId}`

Each source has a display name and a `media_source` URL. Current sources may point at `https://player.sloflix.com` with a signed upstream source in the query string. Treat the entire URL as opaque and short-lived. If `subtitle_location` is present, the web client resolves it under `https://www.sloflix.com/subtitles/`.

Mapping: `PlaybackRepository.stream(session, titleId)`. Use the same authenticated details request and select a source; preserve alternatives for fallback.

## Progress and continue watching

### Save progress

`POST /media/{titleId}/player/metadata`

Auth: required.

Body:

```json
{
  "watch_time": 123.45
}
```

Successful response:

```json
{
  "code": 200,
  "status": "success"
}
```

`watch_time` is the player's current position in seconds and may be fractional. The production player posts a `player.events.watchTime` message every 10 seconds while playing; the SPA forwards its `metadata` object unchanged. The interval stops on pause, and no separate pause flush was found.

Mapping: `PlaybackRepository.saveProgress(session, progress)`.

### Load progress

No separate request is needed. Read:

```json
{
  "metadata": {
    "watch_time": 123.45
  }
}
```

from authenticated `GET /media/single/{titleId}?dont_count_view=true`. `watch_time` is nullable before any progress is saved.

Mapping: `PlaybackRepository.loadProgress(session, titleId)`.

### Continue watching

**Not available.** No dedicated endpoint, catalog query parameter, or production SPA call for a continue-watching collection was found. `has_watched` in catalog items and `metadata.watch_time` in details are per-title fields, but the API does not expose a way to request all in-progress titles.

Mapping: `CatalogRepository.continueWatching(session)` must return an empty result or disable the feature until an endpoint is discovered.

## Repository mapping summary

- `AuthRepository.login` → `POST /user/login`
- `AuthRepository.validateSession` → local JWT `exp` check + `GET /user/preferences/`
- `CatalogRepository.categories` → `GET /genre`, mapped as categories
- `CatalogRepository.titles` → `GET /media`
- `CatalogRepository.filterOptions` → `GET /genre` + static type/sort options
- `CatalogRepository.details` → `GET /media/single/{titleId}?dont_count_view=true`
- `CatalogRepository.continueWatching` → **Not available**
- `PlaybackRepository.stream` → `GET /media/single/{titleId}`, extract `media_sources`
- `PlaybackRepository.saveProgress` → `POST /media/{titleId}/player/metadata`
- `PlaybackRepository.loadProgress` → `GET /media/single/{titleId}?dont_count_view=true`, extract `metadata.watch_time`

## Cloudflare, CORS, and Android OkHttp

- Both `www.sloflix.com` and `api.sloflix.com` are behind Cloudflare. Raw `curl` requests received HTTP 403 with `cf-mitigated: challenge`, while a real Chrome session succeeded.
- The API CORS preflight advertises `GET, POST, OPTIONS, PUT, PATCH, DELETE` and allows `X-Requested-With, Content-Type, Authorization`.
- The observed `Access-Control-Allow-Origin` is the exact web origin (`https://www.sloflix.com` or the currently served canonical origin), not `*`.
- Native Android OkHttp is not subject to browser CORS, so it does not need to send `Origin`. Cloudflare bot mitigation still applies independently and may challenge a non-browser TLS/User-Agent profile. Treat an HTML challenge response or `cf-mitigated: challenge` as an infrastructure/authentication failure, not JSON.
- Do not persist or log bearer tokens, signed `media_source` URLs, Cloudflare cookies, or subtitle identifiers. No Cloudflare cookie was required by the API calls made inside the verified browser session, but this may change.
- The API sometimes reports authentication failure as HTTP 500 with a JSON `status: "failed"` envelope. Parse both HTTP status and the envelope instead of assuming only 401/403.
