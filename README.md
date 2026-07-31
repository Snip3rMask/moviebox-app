# Pi-Stream

Native **Kotlin** Android streaming app that uses the MovieBox API
(`apig.inmoviebox.com`) — home rows, search, title details, and DASH/HLS
playback with signed-cookie streaming.

Built as a clean-room Kotlin port of the verified API flow (anonymous JWT +
`x-client-token` / `x-tr-signature` HMAC signing).

## Features
- Home: auto-scrolling hero carousel + Trending / Bollywood / Hollywood / Korean &
  Turkish Drama / Anime rows
- Bottom navigation: Home / Downloads / Settings tabs (fragments kept alive)
- Full-text search
- Detail page: poster, meta, IMDb score, description, audio (dub) selection
- Series: season → episode list from `season-info`
- Playback: Media3 ExoPlayer with DASH + HLS support, streams the MPD with the
  CloudFront `signCookie` sent as a `Cookie` header
- GitHub Actions: tag push → build APK → **GitHub Release** (no artifact step)

## Project layout
```
shared/src/main/kotlin/msr/pistream/shared/   # shared data layer (Android + PC)
├── data/
│   ├── ApiConfig.kt           # API constants, device info, HMAC key
│   ├── MovieBoxApi.kt         # signed HTTP client (JWT + HMAC)
│   ├── MovieBoxRepository.kt  # high-level data access + dub fallback
│   └── model/                 # Subject / Dub / Episode / SeasonInfo / PlayStream

app/src/main/java/msr/pistream/app/
├── data/
│   ├── ApiConfig.kt           # API constants, device info, HMAC key
│   ├── MovieBoxApi.kt         # signed HTTP client (JWT + HMAC)
│   ├── MovieBoxRepository.kt  # high-level data access + dub fallback
│   └── model/
│       ├── Subject.kt
│       ├── Dub.kt
│       ├── Episode.kt
│       ├── SeasonInfo.kt
│       └── PlayStream.kt
└── ui/
    ├── MainActivity.kt        # nav shell (Home/Downloads/Settings)
    ├── HomeFragment.kt        # home rows + hero carousel
    ├── DownloadsFragment.kt   # downloads tab (empty state)
    ├── SettingsFragment.kt    # settings tab (version/package)
    ├── SearchActivity.kt      # search grid
    ├── DetailActivity.kt      # detail + episodes + dubs
    ├── PlayerActivity.kt      # ExoPlayer (DASH/HLS + cookie)
    └── adapter/
        ├── PosterAdapter.kt
        ├── EpisodeAdapter.kt
        └── CarouselAdapter.kt
```

## Build locally
Prerequisites: JDK 17, Android SDK (platform 34), Gradle 8.9 (via wrapper).

```bash
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # release APK (signs with app/debug.keystore by default)
```

Output: `app/build/outputs/apk/{debug,release}/app-{debug,release}.apk`

## Release via GitHub Actions (direct Release, no artifacts)
1. Push this repo to GitHub.
2. (Recommended) Add repo **secrets** for signing:
   | Secret | Value |
   |---|---|
   | `KEYSTORE_BASE64` | `base64 -w0 release.keystore` output |
   | `KEYSTORE_PASSWORD` | keystore password |
   | `KEY_ALIAS` | key alias |
   | `KEY_PASSWORD` | key password |
   If secrets are missing, the workflow still builds and signs with the bundled
   debug keystore so you always get a release.
3. Tag & push:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
4. The workflow builds `assembleRelease`, then creates a **GitHub Release**
   named `Pi-Stream 1.0.0` with the APK attached directly
   (`.github/workflows/build-release.yml`, `softprops/action-gh-release`).

You can also run it manually: **Actions → Build & Release APK → Run workflow**
(version defaults to `1.0.0`, release tag `release-<version>`).

## API notes
- Anonymous JWT: ping `tab/ranking-list?...` → read `x-user` response header.
- Every request needs:
  - `x-client-token`: `<ts>,<md5(reversed ts)>`
  - `x-tr-signature`: `<ts>|2|<base64(HmacMD5(canonical))>`
  - `x-client-info`: fake Android device JSON
- HMAC key: double base64-decoded plugin constant (see `MovieBoxApi.kt`).
- Streams: `subject-api/play-info?subjectId=..&se=..&ep=..` →
  `data.streams[]` with `url`, `format`, `resolutions`, `signCookie`.
  **Movies use `se=0&ep=0`; series use the actual season/episode.**
- The `signCookie` must be sent as `Cookie` header on the MPD + segment
  requests (CloudFront signed cookie).

> Note: content licensing belongs to MovieBox/rights holders — use responsibly.
