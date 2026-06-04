# AGENTS.md

## Repository State

- `1.md` is the Chinese product requirements document; the Android app skeleton lives under `app/`.
- A Gradle Wrapper is not committed yet. Do not tell users to run `./gradlew` until wrapper files exist; use Android Studio, a local Gradle install, or the GitHub Actions workflow.
- CI exists at `.github/workflows/release.yml`; pushing a `v*` tag builds `:app:assembleRelease`, creates a GitHub Release, and uploads the APK.
- No lockfile, lint config, test config, or generated-code setup exists yet.

## Product Constraints From `1.md`

- Target platform is Android for phase one; keep iOS only as a future architecture consideration.
- Preferred implementation is native Android with Kotlin and Jetpack, using Fragment/XML UI to minimize APK size.
- Avoid Flutter/React Native unless requirements change; the PRD explicitly rejects them for size/performance reasons.
- Core UX is local PDF grouping plus continuous vertical reading across multiple PDFs, with reading progress saved by group.
- Keep the app fully local: no registration, ads, cloud sync, network, or location dependencies.
- Size/performance goals are strict: Android APK under 8 MB, cold start under 500 ms, 60 fps reading, and bounded bitmap memory via visible-page rendering and recycling.

## Implementation Notes To Preserve

- Keep implementation native Android/Kotlin with Fragment/XML UI unless requirements change.
- PDF scanning should use MediaStore/SAF and common directories such as Download, Documents, and Tencent/QQ receive folders.
- Default group ordering uses natural filename sort, e.g. `1.pdf, 2.pdf, 10.pdf`, with manual drag reorder support.
- Rendering should prioritize `PdfRenderer` with `Pdfium` only as a fallback or optional compatibility path.
- Data model concepts in `1.md` are `PdfFile`, `PdfGroup`, `GroupPdfCrossRef`, and `ReadingProgress`; preserve progress at `groupId + currentPdfId + currentPage + pageScrollOffset` granularity.
- UI navigation is intentionally only two tabs: file library and groups; reader settings live inside the reading UI, not a separate profile/settings tab.

## Current Entrypoints

- `app/src/main/java/com/lightread/pdfreader/MainActivity.kt` owns the two-tab shell.
- `ui/library/LibraryFragment.kt` imports/scans PDFs and creates groups.
- `ui/groups/GroupsFragment.kt` opens saved groups.
- `ui/reader/ReaderActivity.kt` builds one continuous RecyclerView page stream across all PDFs in a group.
- `data/PdfLibraryStore.kt` currently persists local state as JSON to avoid codegen while the skeleton is small; migrate deliberately if adding Room.
