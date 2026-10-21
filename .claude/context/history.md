# PlexLite — change history and rationale

Chronological log of what was built, in which order, and why. Newest at the bottom. Append a short
entry for every meaningful change so future sessions keep the thread.

1. **First version** — native Android TV client: PIN sign-in (plex.tv/link), connection racing,
   Home (Continue watching + hubs), library grid, movie/show detail, ExoPlayer with direct play via
   the MDE decision, FFmpeg audio, HLS fallback, timeline, skip intro/credits, next episode.
2. **Token on media requests + decision logging** — the first real test failed with HTTP 404s; logs
   were needed to understand what the server decided.
3. **File availability check (`checkFiles=1`)** — the 404 came from the server not finding the file.
4. **Single-object `OnDeck`** — a show detail crashed because PMS returned `OnDeck.Metadata` as an object.
5. **Friendly error panel with OK button** — raw ExoPlayer error codes were unreadable on a TV.
6. **First available media version** — the "missing" file was a stale version; Plex Web played the
   other version. Root cause of the owner's "transcoding problems" with *La isla de las tentaciones*.
7. **Failover across versions and transcoding** — owner asked that failures try the next option.
8. **Visible focus in the player** — CC/settings buttons worked but focus was invisible and the
   controller hid after 4 s, so they seemed unreachable.
9. **Audio button + custom settings menu** (playback mode, speed) — owner wanted language switching
   separate from settings and a way to force transcode quality.
10. **Hidden controls on start + accumulated arrow seeking** — controls covered the video on start;
    ←/→ only jumped once per press. Overlay restyled twice (smaller, translucent, then centered).
11. **Custom subtitle menu + OpenSubtitles search + subtitle size** — match the official app's search,
    four sizes with the previous size as "Normal".
12. **Server quick menu + i18n** — header server name became a dropdown; app localized. Languages were
    later changed to: en, es, fr, de, it, pt, ru, zh, ar, hi (Bengali and Urdu removed, German and
    Italian added, in that display order).
13. **Server switching hang fix** — connection probes queued behind image downloads on OkHttp's shared
    dispatcher (5 requests per host) and timed out; probes now have their own dispatcher. Switching no
    longer forgets the current server; options hidden when there is only one server.
14. **Back in the server menu** returns to the previous level instead of closing everything.
15. **Plex Home profile picker** with avatars, last-used user, 5 s auto sign-in, PIN keypad, cached
    per-user sessions; "Switch user" in the menu; avatar next to the server name.
16. **Remember user choice** — checkbox on the picker + Settings switch to skip the picker on launch.
17. **Library ordering/hiding + "Inicio" tab + redesigned Settings** — library bar also inside libraries;
    Home rows follow the order (global `home.*` hubs stay in place). Settings in grouped cards with
    dropdowns for quality and language.
18. **Organizer rework** — OK moves directly (no popup), list follows the moving item, the dropping OK
    no longer re-triggers a click (KeyUp swallowed), eye button to hide/show; red "Sign out" at the bottom;
    clearer filter labels ("Mostrar solo películas no vistas" / "Mostrar todas las películas").
19. **Organizer polish + filter flicker** — removed numbers, eye moved to the left, initial focus on the
    first library; the filter no longer clears the grid (results swap in place, focus stays on the button).
20. **Search** — magnifier chip before "Inicio" in the library bar opens an on-screen keyboard (a–z, ñ, 0–9,
    space, delete, clear; physical keys also type) with debounced `/hubs/search` results grouped into movies,
    shows and episodes, filtered by hidden libraries. No voice search (owner's decision).
21. **Richer detail page** — IMDb / Rotten Tomatoes / TMDB rating badges (from `Rating[]`), "Directed by",
    pre-play chips for video quality, audio track and subtitle (dropdowns that persist the selection on the
    server), cast & crew row with circular photos (`Role[]`), trailers & extras row (`includeExtras=1`,
    plays like any item). Related movies intentionally omitted (owner's decision).
22. **Card badges** — ✓ in a dark circle for watched items (replaces the orange dot) and an unwatched
    episode count pill on shows/seasons (`leafCount - viewedLeafCount`), matching the official app.
