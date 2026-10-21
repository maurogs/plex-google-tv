# PlexLite — project context

## Why this project exists

The official Plex app on the owner's Google TV (TCL mini LED, "Smart TV Pro", Android TV 11, 32-bit
`armeabi-v7a`, 1080p UI at density 320) became slow and unreliable after Plex's UI redesign:

- The UI stutters (it is effectively a web app).
- Videos spend a long time buffering before starting.
- Some videos that used to direct play started failing with "transcoding problems".

The Plex Media Server runs on **Unraid in another city**, so every request goes over the WAN
(remote `*.plex.direct` HTTPS connection; the two LAN addresses Plex advertises are unreachable
from the TV's network). Bandwidth and round trips matter.

Inspiration: [GLinnik21/plx-native](https://github.com/GLinnik21/plx-native), a native Rust/C Plex
client for LG webOS that is much faster than the official app. It is GPL-3.0 and webOS-specific, so
**no code was copied**; it was only used as a source of Plex protocol knowledge (its `docs/pms-api.md`
is the most accurate description of the real PMS API, better than Plex's own OpenAPI spec).

**Goal:** a fast, native, remote-friendly Plex client for Google TV that reliably direct plays, with
a UI tailored to the owner's preferences.

## Stack

- Kotlin, Jetpack Compose for TV (`androidx.tv:tv-material`), single `app` module.
- Media3/ExoPlayer 1.9.x with the Jellyfin FFmpeg audio decoder (`org.jellyfin.media3:media3-ffmpeg-decoder`,
  pinned to 1.9.x to match Media3's ABI) so DTS/TrueHD/etc. decode on-device instead of forcing a transcode.
- OkHttp 5 + kotlinx.serialization (lenient JSON), Coil 3 for images.
- No DI framework: `AppGraph` (object) holds singletons and app-wide Compose state.
- AGP 9.4, Kotlin 2.4, compileSdk 37, minSdk 26. Built with JDK 21 from Homebrew.
- Release builds use R8 and the debug signing key (sideloaded). Always test release builds on the TV:
  Compose debug builds are noticeably slower.

## Code map (`app/src/main/java/dev/plexlite/tv`)

| Area | Files | Notes |
|---|---|---|
| App/state | `PlexLiteApp.kt`, `data/AppGraph.kt` | Coil config; `AppGraph` = prefs, identity, current server, user switching, library layout, server/home-user counts (Compose state). |
| Plex API | `data/PlexTv.kt` (plex.tv), `data/PlexServer.kt` (PMS), `data/Http.kt`, `data/PlexIdentity.kt`, `data/Models.kt` | All PMS calls send `Accept: application/json` and `X-Plex-*` identity headers. |
| Connection | `data/ConnectionPicker.kt` | Races all connections in parallel (`/identity`, must match `machineIdentifier`), local→remote, relay only as last resort. Uses its **own OkHttp dispatcher** so probes don't queue behind image downloads. |
| Device caps | `data/DeviceProfile.kt` | Detects hardware video decoders; builds `X-Plex-Client-Profile-Extra` (direct-play profile with all FFmpeg-decodable audio and SRT/ASS/PGS/VobSub subtitles). |
| Persistence | `data/Prefs.kt` | SharedPreferences + JSON: auth token, current server, quality, subtitle size, language, current user, per-user sessions, auto sign-in, library layouts. |
| i18n | `data/AppLocale.kt`, `res/values*/strings.xml` | 10 languages (en default, es, fr, de, it, pt, ru, zh, ar, hi). Activities wrap their context with the chosen locale. |
| Player | `player/PlayerActivity.kt`, `player/StreamResolver.kt`, `player/OverlayMenu.kt` | View-based (`PlayerView`) with custom controls/menus. |
| UI | `ui/MainActivity.kt` (navigation), `ui/home`, `ui/library`, `ui/detail`, `ui/login`, `ui/profiles`, `ui/search`, `ui/settings`, `ui/components` | Compose. `HeaderActions` + `LibraryBar` shared by Home and Library screens. `ui/home/Hero.kt` holds the debounced dynamic hero (`DebouncedHero`), reused for the focused-episode info on show pages. `ui/home/HubFeed.kt` is the hero + rows feed shared by Home and a library's Recommended tab. |

## Playback design (the core of the "speed/transcoding" fix)

1. Fetch item metadata with `checkFiles=1`; build the list of **media versions whose files exist**
   (some items have stale versions pointing at moved files — Plex Web silently picks the good one).
2. For the chosen version, ask PMS the MDE decision (`/video/:/transcode/universal/decision`,
   `directPlay=1`, our profile-extra). If `directplay` → stream the part URL directly
   (`/library/parts/...`), with sidecar subtitles attached.
3. Otherwise HLS transcode (`start.m3u8`, H.264 target, burned-in subtitles if one is selected).
4. **Failover chain** on any error: version 1 direct → version 1 transcode → version 2 direct → … ;
   the friendly error panel (with an OK button) only appears when everything fails.
5. Startup speed: no speed probe, ExoPlayer starts at ~1.5 s buffered, buffers up to 2 min ahead,
   resumes at `viewOffset`.
6. Timeline reported every 10 s and on state changes; transcode sessions stopped on exit.

Player UI: controls hidden on start (OK shows them); ←/→ accumulate seeks (10 s per press, single
seek after 0.7 s, centered translucent "« 30 s / 12:39 / 57:52" indicator); bottom bar = CC (custom
subtitle menu incl. OpenSubtitles search/download via the server), audio tracks, settings
(playback mode direct/transcode bitrates, speed, subtitle size). Skip intro/credits and next-episode
hints use the OK button.

## Hard-won Plex API facts (see plx-native `docs/pms-api.md` for more)

- Use each server's own `accessToken` from `/api/v2/resources`; use connection `uri`s verbatim.
- `OnDeck.Metadata` can be a single object, not an array (custom serializer in `Models.kt`).
- `librarySectionID` and `score` may be numbers or strings → model them as `String?` (lenient JSON).
- Hubs whose `hubIdentifier` starts with `home.` are global (span several libraries).
- Subtitle search: `GET /library/metadata/{rk}/subtitles?language=<2-letter>` (3-letter codes → HTTP 500);
  download with `PUT …/subtitles?key=<candidate key>`; it installs asynchronously, so poll the item.
- Audio/subtitle selection persists per item with `PUT /library/parts/{id}?allParts=1&audioStreamID=…`.
- Plex Home: `GET /api/v2/home/users` (account token), switch with
  `POST /api/v2/home/users/{uuid}/switch[?pin=]`, then refetch resources with the user's token.
- The server must see the file: an HTTP 404 on the part URL usually means a stale media version.

## Product decisions made with the owner

- Spanish conversation; **all docs in English**; Claude never commits — it proposes a commit message.
- Profile picker ("¿Quién está viendo?") on launch with 5 s auto sign-in countdown for the last user,
  plus "remember my choice" checkbox / Settings switch to skip it. PIN users always ask for the PIN.
- The header dropdown shows the current user (avatar + name, max 260 dp with ellipsis; server name as fallback):
  Switch user (only if >1 Home user), Change server (only if >1 server, shows the current server), App language,
  Organize libraries, Settings.
- Library bar: user menu, fixed search icon, divider, then a scrolling row with "Inicio" first and the libraries in the
  user's order. The row scrolls minimally and fades out softly on the side with more chips (hiding cut chips
  was tried and rejected).
- Each library has two tabs: "Recommended" (default; the server's section hubs, Continue watching first) and
  "Library" (full grid, filter, sort, A–Z). The tab is remembered for the session across libraries, not persisted.
  "Categories" was declined.
- Inside a library the library bar is hidden; Back or ↑ from the tabs shows it focused on the current library;
  Back from the bar goes Home with focus on the "Home" chip. Tabs are text tabs (accent underline); filter/sort are compact `ToolbarChip`s — avoid
  rows of identical pill buttons.
- Organize libraries: OK enters move mode (↑↓, OK drops, Back cancels); eye button on the left hides/shows.
  Layout is stored per user and per server; hidden libraries disappear from the bar and from Home rows
  (global rows just lose those items); "Continue watching" always shows everything.
- Settings: grouped rounded cards; quality and language as dropdowns; red "Sign out" at the very bottom.
- Library filter button label describes the action ("Mostrar solo películas no vistas" / "Mostrar todas
  las películas"); alternatives B/C were offered and are still open.
- Explicitly declined by the owner: voice search, related movies, watchlist ("My Lists"), the official app's
  online tabs (For You / Trending / Activity, Recommended / Explore / Categories), star ratings, reviews,
  collapsible side rail, clock. Speed and a flawless remote-control UX take priority over features.

## How we work

- Build: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:assembleRelease`
- TV: `adb` at `/opt/homebrew/share/android-commandlinetools/platform-tools/adb`, TV at
  `192.168.1.39:5555` (USB debugging over Ethernet; `adb connect` again if it drops).
- Before installing, check the TV isn't playing something (`dumpsys activity activities | grep mResumedActivity`);
  installing kills the app. Relaunch with `am start -n dev.plexlite.tv/.ui.MainActivity`.
- Verify UI changes on the real TV with `input keyevent` + `screencap` and read the screenshots.
  Be careful: scripted key presses can land on the wrong screen — never leave the owner's settings
  (library order/visibility, filters, language, user) changed; restore anything touched.
- Logs: `adb logcat -s PlexLite:*` (tokens are redacted in logged URLs).
- Translations: every new string must be added to all 10 `values*/strings.xml` files.
- Report honestly what was verified on the TV and what was not.

## Open ideas / not done yet

- Per-show remembered audio/subtitle choice; persistent playback speed.
- Sync library order/visibility with Plex's own pinned libraries (undocumented API).
- The multi-server picker flow and PIN-protected users were never tested (owner has one server, no PIN users).
- Subtitle download end-to-end was never confirmed on the real server.
