# 轻阅 · PDF 分组阅读器

Android-first local PDF group reader based on `1.md`.

Current implementation status:

- Native Android Kotlin project using Fragment/XML UI.
- Two top-level tabs: file library and groups.
- Local PDF import through MediaStore scan and SAF multi-document picker.
- Natural filename ordering for group creation.
- Continuous vertical reader backed by Android `PdfRenderer`, rendering visible RecyclerView pages and recycling bitmaps.
- Reading progress is stored locally at `groupId + currentPdfId + currentPage + pageScrollOffset` granularity.

Build note:

- A Gradle Wrapper is not committed yet because this environment has no global `gradle` command to generate it.
- Open the project in Android Studio, or add a wrapper with a local Gradle install before using command-line builds.
- Resource compilation was checked with SDK `aapt2`; full Kotlin/Android compilation still needs Gradle.
