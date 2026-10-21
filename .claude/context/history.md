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
23. **Dynamic hero on Home and backdrop in libraries** — the focused card drives a backdrop + title/episode/
    meta/summary block (250 ms debounce, 400 ms in libraries); the focused row snaps under the hero. Hero state
    is read only inside `DebouncedHero` so focus changes don't recompose the rows (measured on the TV: janky
    frames 10.3% → 7.3%, p95 38 ms → 19 ms).
24. **Library sort + A–Z rail** — "Sort: Title ▾" dropdown (title, year, date added, rating; remembered per
    library) next to the unwatched filter. With title sort, a compact A–Z rail on the right uses
    `/library/sections/{id}/firstCharacter` counts; jumping fetches the missing range in a single request and
    focuses the first item of that letter. The current letter is highlighted while browsing.
23. **Dynamic hero** — Home shows the focused item's backdrop, title, metadata, summary and cast at the
    top (debounced 250 ms in `ui/home/Hero.kt` so fast scrolling stays smooth); libraries show the focused
    item's backdrop. Performance first: no extra requests per focus beyond the already-cached images.
24. **Library sort + A–Z rail** — sort dropdown (`LibrarySort` in `Models.kt`) and a letter rail to jump
    through the grid; item count next to the filter.
25. **Clearer episodes** — episode cards show an "E1, E2…" badge next to the watched ✓, and the focused
    episode's number, title, duration and summary appear above the row (same debounced hero mechanism).
    Watchlist ("My Lists"), voice search and related movies were explicitly declined by the owner.
26. **Detail page layout fix** — the title was scrolled off-screen because Compose for TV's default
    bring-into-view pivot moved the focused Play button towards the middle; the detail list now uses a
    minimal-scroll `BringIntoViewSpec`. Action row redesigned: white primary pill (accent when focused) and
    round icon buttons (restart, watched) that expand to show their label on focus; quality/audio/subtitle
    chips moved to their own compact row (the subtitle chip no longer truncates to "…"); cast photos 112 → 72 dp.
27. **User name in the header** — the header dropdown now shows the current Plex Home user (avatar + name,
    e.g. "Tucli") instead of the server name, capped at 260 dp with an ellipsis for long names; falls back to
    the server name when no user is known. "Change server" (still only shown with >1 server) now shows the
    current server name underneath, since the header no longer does.
28. **Library tabs: Recommended / Library** — like the official app, each library opens on "Recommended"
    (`/hubs/sections/{id}`: Continue watching, Recently released, Recently added, … in the server's order),
    rendered with the same hero + rows feed as Home (extracted to `ui/home/HubFeed.kt`). "Library" is the
    existing grid with filter, sort and A–Z rail (loaded lazily on first open). The chosen tab is kept in
    `AppGraph.libraryTab` for the session only, so switching libraries keeps it. The grid controls moved to a
    second row (the sort label wrapped onto three lines otherwise), and switching tabs keeps focus on the tab.
    "Categories" was skipped (owner's call).
