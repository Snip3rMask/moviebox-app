# Pi-Stream Conversion Plan

Target: production-grade, open-source friendly, multi-source streaming app
with CloudStream-style source switching. Every phase ends with a green CI +
a tagged release.

> **Source model (confirmed):** all sources are **compiled into the app** as
> normal Kotlin code — exactly like MovieBox today (API reverse-engineered
> from the extension, integrated directly). There is **no runtime extension
> loading** (no .cs3 / classloader / sandbox). Adding a source = adding a
> Kotlin file + registering it; the source picker UI just lists what is built in.

Current baseline: `v0.4.3` (Android APK + Windows EXE, direct GitHub Release).

---

## Goals

1. Fix known data bug (`stills` dict-vs-list breaks 18/60 detail pages).
2. MVVM + package-by-feature architecture (testable, survives config changes).
3. Multi-source system: every built-in source implements one `Source`
   contract; user switches between them in-app like CloudStream.
4. Open-source readiness: tests, lint, CI on PRs, LICENSE/CONTRIBUTING, docs.
5. Same sources on Android and Windows (shared data layer already exists).

## Non-Goals

- **Runtime extension loading is not planned at all.** Sources are compiled in
  (like MovieBox today); no .cs3/classloader/sandbox ever.
- Migrating the Android UI to Compose (XML views stay; desktop is Compose).
- Hilt/DI framework (manual `AppContainer` first; Hilt later if needed).

---

## Target Architecture

```
shared/src/main/kotlin/msr/pistream/shared/
├── model/                      # provider-agnostic common model
│   ├── Media.kt                # id, title, poster, year, rating, genres, lang
│   ├── MediaDetail.kt          # description, cast, trailers, stills, dubs
│   ├── Episode.kt              # se/ep + label (+ subjectId owned by source)
│   ├── Stream.kt               # url, format, quality, cookie/headers
│   └── Subtitle.kt             # lan, label, url, delay
├── source/
│   ├── Source.kt               # interface (contract)
│   ├── SourceManager.kt        # sources list + activeSource: StateFlow<Source>
│   ├── moviebox/               # current MovieBox logic moves here
│   │   ├── MovieBoxSource.kt   # implements Source
│   │   ├── MovieBoxApi.kt      # signed HTTP client (internal, class not object)
│   │   └── MovieBoxMapper.kt   # DTO -> common model (handles stills dict/list)
│   └── anifux/ ...             # future sources
└── repository/
    └── MediaRepository.kt      # facade delegating to SourceManager.activeSource

app/src/main/java/msr/pistream/app/
├── di/AppContainer.kt          # manual DI: builds repo, sources, source manager
├── ui/
│   ├── home/    HomeScreen/HomeViewModel/HomeUiState
│   ├── search/  SearchScreen/SearchViewModel/SearchUiState
│   ├── detail/  DetailScreen/DetailViewModel/DetailUiState
│   ├── player/  PlayerScreen/PlayerViewModel/PlayerUiState
│   ├── settings/  downloads/
│   ├── sourcepicker/SourcePickerSheet.kt
│   └── common/  adapters, widgets, theme
└── crash/                      # unchanged
```

Per-screen pattern:
- `XViewModel` owns state: `private val _state = MutableStateFlow<XUiState>(Loading)`
- UI collects with `repeatOnLifecycle(STARTED)`; renders Loading/Success/Error
- Errors show a retry UI, not just a Toast
- ViewModel reads `SourceManager.activeSource` -> source switch refreshes all

---

## Phases

### Phase 0 — Stills bug fix (v0.4.4)
- Fix `Subject.stills` decode: accept dict OR list via custom serializer
  (`StillsSerializer` / `JsonElement` + computed `stillsList`)
- Verify: open a title whose API returns `stills: {...}` (e.g. home feed 18/60)
- Release v0.4.4

### Phase 1 — Tooling foundation (v0.5.0)
- `gradle/libs.versions.toml` (version catalog; all deps centralized)
- `ktlint` (or Spotless) + `.editorconfig`; `detekt` optional
- `ci.yml`: PR pipeline = compile + lint + shared unit tests (fast, no release)
- Keep `build-release.yml` for tag pushes unchanged
- Add JUnit + MockWebServer + coroutines-test test deps

### Phase 2 — Data layer refactor (v0.5.1)
- `MovieBoxApi` object -> class (injectable `OkHttpClient`, instance token state)
- `MovieBoxRepository` -> interface + `DefaultMovieBoxRepository`
- `AppContainer` wires real implementations (desktop uses its own wiring)
- Unit tests (shared, JVM):
  - signing: `canonicalUrl` + HMAC signature golden tests
  - endpoints: login/detail/play-info/captions with MockWebServer
  - repository: dub fallback, resolveStream owner, captions parse
- Verify desktop still builds against the new interface

### Phase 3 — Common model + Source contract (v0.6.0)
- Add provider-agnostic `Media/MediaDetail/Stream/Subtitle`
- Add `Source` interface + `SourceManager` (activeSource StateFlow)
- Extract `MovieBoxSource` + `MovieBoxMapper` (fixes stills once, everywhere)
- `MediaRepository` facade delegates to active source
- Keep old `Subject` DTOs in `moviebox/` (internal to the source)
- Tests: mapper (dict/list stills, empty fields), SourceManager switch behavior

### Phase 4 — MVVM screens (v0.6.x, biggest phase)
- 4a Home + Search: HomeViewModel/SearchViewModel + UiState + retry UI
- 4b Detail: DetailViewModel (detail, seasons, episodes, dubs, trailer)
- 4c Player: PlayerViewModel (current episode, dub, subtitle list, captions,
  player lifecycle hooks; ExoPlayer stays in the UI layer)
- UI switches from `lifecycleScope.launch` to `repeatOnLifecycle` collection
- Rotation now survives without re-fetch (ViewModel scope)
- Tests: ViewModel unit tests with fake source (Turbine or plain collect)

### Phase 5 — Source picker UI (v0.7.0)
- `SourcePickerSheet`: bottom sheet listing sources (name, langs, icon) —
  CloudStream-style; select -> `SourceManager.switch(id)`
- Home top bar: source badge/button (shows active source)
- Settings -> "Sources" section (active + installed list)
- Active source persists (DataStore/SharedPreferences) across launches
- All ViewModels react to `activeSource` change automatically

### Phase 6 — Navigation (v0.7.1)
- Add Navigation Component (fragment nav graph) for MainActivity tabs
- Detail/Player stay as activities started via nav actions or kept as intents
- Remove manual `FragmentManager` tab switching in `MainActivity`

### Phase 7 — Open-source hardening (v0.8.0)
- `LICENSE` (MIT or Apache-2.0 — recommend Apache-2.0)
- `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, issue/PR templates
- README rewrite: correct project layout (data lives in `shared/`), source
  system docs, build/test instructions, screenshots
- `docs/architecture.md` (this plan becomes the architecture doc)
- `SECURITY.md` (API keys/endpoints are reverse-engineered; responsible use)

### Phase 8 — Desktop refactor (v0.8.1)
- Split 380-line `Main.kt` into `home/`, `detail/`, `sourcepicker/` packages
- Compose side uses `SourceManager` + `MediaRepository` (same shared sources)
- Reuse common model; keep mpv player launcher

---

## Version / Release Cadence

- Every phase = tag push -> CI builds APK + EXE -> same GitHub Release
- `v0.4.4` (stills) -> `v0.5.x` (tooling/data) -> `v0.6.x` (sources/MVVM)
  -> `v0.7.x` (picker/nav) -> `v0.8.x` (docs/desktop)
- No artifacts workflow change; direct Release already works
- Adding a future source: `source/<name>/<Name>Source.kt` + register in
  `SourceManager` -> shows up in the picker automatically, no UI change

## Definition of Done (each phase)

- `ci.yml` green: compile (app + shared + desktop), lint, shared tests
- Release tag green: APK + EXE attached
- README/docs updated for the phase
- No silent-failure UI paths (error states exist)
- Desktop still builds and runs the same sources

## Risks / Decisions

- MovieBox API is reverse-engineered and may change; keep mapper isolated so
  only `MovieBoxSource` needs patching.
- Multi-source normalization: per-source mappers hide API quirks (stills,
  missing episodes, empty dubs) behind the common model.
- All sources are first-party compiled code; no third-party code execution
  risk from runtime extensions.
- XML views stay for Android; Compose only on desktop (less churn).
- Manual DI (`AppContainer`) keeps the shared JVM layer desktop-friendly.
