# 轻阅 · PDF 分组阅读器

轻阅是一个 Android 本地 PDF 分组阅读器，面向需要连续阅读多份 PDF 的场景。应用重点解决散乱 PDF 的导入、分组、排序、续读和跨文件连续阅读问题。

## 主要功能

- 本地 PDF 文件库：支持自动扫描设备 PDF，也支持系统文件选择器手动导入。
- PDF 分组：可把多份 PDF 组成一个阅读分组，支持编辑分组名称、调整分组内容和删除分组。
- 自然排序：创建分组时按文件名自然排序，例如 `1.pdf, 2.pdf, 10.pdf`。
- 连续阅读：阅读器将分组内多个 PDF 串成一个竖向连续页面流。
- 阅读进度：按分组保存当前 PDF、页码和页内滚动位置，重新打开后继续阅读。
- 目录跳转：阅读器内可打开目录，直接跳转到分组内任意 PDF。
- 远端源下载：底部“源”tab 可添加 rclone HTTP 源，整页浏览远端目录并下载 PDF，下载后按远端目录自动创建分组。
- 远端浏览页面：远端源内容独立显示，可查看当前 URL、目录/PDF 数量和读取错误。
- 下载进度：远端目录下载时显示文件数量、当前文件和完成进度，避免长时间等待时没有反馈。
- 远端目录兼容：自动忽略 rclone 页面里的 zip 下载、排序和返回链接，只保留真实目录与 PDF。
- 在线更新：启动时和关于页可检查更新，新版本可在应用内自动下载 APK，并打开系统安装确认页。

## 页面结构

- 文件库：扫描、导入 PDF，并选择 PDF 创建分组。
- 分组：查看已有分组，进入阅读器，编辑或删除分组。
- 关于：查看当前版本，手动检查更新。

## 技术实现

- 原生 Android Kotlin。
- Fragment/XML UI。
- `PdfRenderer` 渲染 PDF 页面。
- RecyclerView 承载连续阅读页面流。
- 本地 JSON 文件保存文件库、分组和阅读进度。
- 通过解析 rclone `serve http` 目录 HTML 模板读取远端目录，不依赖服务端 JSON API。
- `HttpURLConnection` 检查 GitHub Releases，不依赖 Retrofit/OkHttp。

## 构建

- 当前仓库还没有提交 Gradle Wrapper，因为本环境没有全局 `gradle` 命令生成 Wrapper。
- 可用 Android Studio 打开项目构建，或先用本地 Gradle 添加 Wrapper 后再使用命令行构建。
- 已用 Android SDK 的 `aapt2` 验证资源编译；完整 Kotlin/Android 编译仍需要 Gradle。

## 发布

- 推送 `v*` 格式的 tag 会触发 GitHub Actions。
- 工作流会执行 `:app:assembleRelease`，创建 GitHub Release，并上传 `qingyue-<tag>.apk` 与 SHA-256 校验文件。
- 可选正式签名密钥：`ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`。
- 未配置正式签名密钥时，使用仓库内固定的公开 fallback 签名，保证 GitHub Release APK 可覆盖升级。
