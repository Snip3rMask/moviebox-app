# Pi-Stream Roadmap

Living checklist. Update it as work lands — check `[ ]` -> `[x]` and tag the
release so "what's done / what's left" is always verifiable before starting
the next phase. Architecture details: see [migration-plan.md](migration-plan.md).

## Status Legend
- `[x]` shipped (in a tagged release)
- `[ ]` not started
- `[~]` in progress

## Already Shipped (baseline v0.4.3)
- [x] Signed MovieBox API client (JWT + HMAC) in `shared/`
- [x] Home rows + hero carousel + skeleton loading
- [x] Search, detail page (cast/crew/meta/season/dubs/stills/trailer)
- [x] Player: DASH/HLS playback with signed cookie
- [x] Player settings sheet: quality / audio / subtitles / dub switch
- [x] Separate subtitle tracks (caption endpoint, SRT sideload)
- [x] Portrait player layout: video top + episode list below (highlight)
- [x] Crash log system + Settings -> Crash Logs manager (view/share/download/delete)
- [x] CI: tag push -> APK + Windows EXE -> direct GitHub Release
- [x] Android TV / Fire TV left rail + landscape full-screen player

## Phase 0 — Stills Bug Fix (v0.4.4)
- [x] Reproduce: `stills` returns a single dict on ~18/60 titles
- [x] Normalize `stills`: keep raw `JsonElement`, expose `stillsList` (dict OR list)
- [x] Unit tests: object / list / absent / invalid -> correct `stillsList`
- [x] Release v0.4.4 (CI green)

## Phase 1 — Tooling Foundation (target v0.5.0)
- [ ] `gradle/libs.versions.toml` version catalog (all deps centralized)
- [ ] ktlint (or Spotless) + `.editorconfig` wired into build
- [ ] `ci.yml`: PR pipeline = compile (app+shared+desktop) + lint + tests
- [ ] Test deps: JUnit4, MockWebServer, kotlinx-coroutines-test
- [ ] Release v0.5.0

## Phase 2 — Data Layer Refactor (target v0.5.1)
- [ ] `MovieBoxApi` object -> class (injectable OkHttpClient, instance token)
- [ ] `MovieBoxRepository` -> interface + `DefaultMovieBoxRepository`
- [ ] `di/AppContainer.kt` manual DI (Android + desktop wiring)
- [ ] Shared unit tests: HMAC signing, endpoints, dub fallback, captions
- [ ] Desktop still builds against new interface
- [ ] Release v0.5.1

## Phase 3 — Common Model + Source Contract (target v0.6.0)
- [ ] Provider-agnostic models: `Media` / `MediaDetail` / `Stream` / `Subtitle`
- [ ] `Source` interface (homeRows/search/detail/seasons/streams/captions)
- [ ] `SourceManager` with `activeSource: StateFlow<Source>` + `switch(id)`
- [ ] `MovieBoxSource` + `MovieBoxMapper` (stills dict/list handled here)
- [ ] `MediaRepository` facade delegates to active source
- [ ] Mapper + SourceManager unit tests
- [ ] Release v0.6.0

## Phase 4 — MVVM Screens (target v0.6.x)
- [ ] 4a Home + Search: ViewModel + UiState + retry UI (repeatOnLifecycle)
- [ ] 4b Detail: DetailViewModel (detail/seasons/episodes/dubs/trailer)
- [ ] 4c Player: PlayerViewModel (episode/dub/subtitle/captions state)
- [ ] Rotation survives without re-fetching (ViewModel scope)
- [ ] ViewModel unit tests with fake Source
- [ ] Release v0.6.1 (Home+Search) -> v0.6.2 (Detail) -> v0.6.3 (Player)

## Phase 5 — Source Picker UI (target v0.7.0)
- [ ] `SourcePickerSheet`: bottom sheet (name, langs, icon) CloudStream-style
- [ ] Home top bar source badge/button (shows active source)
- [ ] Settings -> "Sources" section (active + installed list)
- [ ] Persist active source (SharedPreferences/DataStore)
- [ ] All ViewModels react to source switch (auto refresh)
- [ ] Release v0.7.0

## Phase 6 — Navigation (target v0.7.1)
- [ ] Navigation Component nav graph for MainActivity tabs
- [ ] Remove manual FragmentManager tab switching
- [ ] Detail/Player launch path documented (nav actions or intents)
- [ ] Release v0.7.1

## Phase 7 — Open-Source Hardening (target v0.8.0)
- [ ] `LICENSE` (Apache-2.0 recommended)
- [ ] `CONTRIBUTING.md` + `CODE_OF_CONDUCT.md` + issue/PR templates
- [ ] README rewrite (fix stale layout: data lives in `shared/`)
- [ ] `docs/architecture.md` + `SECURITY.md`
- [ ] Release v0.8.0

## Phase 8 — Desktop Refactor (target v0.8.1)
- [ ] Split 380-line `Main.kt` into `home/` / `detail/` / `sourcepicker/`
- [ ] Compose uses `SourceManager` + `MediaRepository` (same sources)
- [ ] Release v0.8.1

## Future Sources (compile-time, picker shows automatically)
- [ ] `source/anifux/AnifuxSource.kt` (+ mapper + register in SourceManager)
- [ ] more sources as needed (same pattern)

> Note: no runtime extension loading. Every source is compiled into the app
> (MovieBox-style). Adding one = new Kotlin file + registry entry, no UI change.
