# 应用内更新（1.4.4 起）

## 用户操作

主页面 **帮助与更新 → 检查更新**，进入更新页后会检查一次。发现新版时显示版本、大小和更新说明，用户点击“下载更新”，校验通过后点击“安装更新”，按 Android 系统提示确认。

首次安装更新时，Android 8 及以上可能要求允许 kegent 安装应用；授权后返回继续。拒绝或取消不会卸载旧版，已下载的文件可保留重试。页面退出会暂停正在进行的下载，重新进入可继续；页面旋转由 ViewModel 保留任务。

路线正在运行时不进行安装，先结束运行再点击安装。下载、校验或打开系统安装器都不代表安装成功；是否完成以系统实际安装的版本为准。正常同签名覆盖安装保留路线库、草稿和设置。

1.4.3 及更早的版本没有更新入口，需要先手动安装一次 1.4.4 或以后含更新模块的兼容签名包。

## 更新源与发布状态

- 默认读取 `https://api.github.com/repos/Haven-mao/kegent/releases/latest`，寻找稳定、非草稿 Release 中名为 `update.json` 的资产。
- 也支持在更新页“更新来源”中填写直接提供清单的 HTTPS 地址，便于使用自己的镜像。更换地址不会跳过签名校验。
- 不解析 Release 正文的 ZIP 链接，不把未发布的本地 APK 当作远程更新。
- 当前代码接入与 GitHub 正式发布是两件事：发布清单和 APK 之前，页面会显示尚未提供更新包；网络不可达时显示连接错误，可重试或打开发布页。
- 更新检查由用户打开页面或点击按钮触发，不持续轮询后台服务。

## 校验与本地状态

下载只允许 HTTPS，重定向各跳也要求 HTTPS，最多跟随 5 次；版本清单最多 128 KiB，APK 最大 250 MiB。暂停时保留 `.part`，恢复时发送 Range，并检查 Content-Range；服务器不支持续传时从头下载。

完整文件按清单大小和 SHA-256 验证，随后用 Android 包管理器检查实际 APK 的包名、versionCode、versionName、minSdk 与签名。版本必须高于当前已安装版本，签名必须与当前安装包的签名集合一致；不接受另一个应用、另一个签名、旧版或字段与 APK 不符的清单。安装前会再次校验。

Android 8 使用旧版签名查询；Android 9 及以上同时请求新旧签名信息，兼容部分模拟器未返回 SigningInfo 的情况。当前采用固定同证书通道，不实现证书轮换。

文件位于应用私有 `files/app-updates/`，不进入系统备份。FileProvider 只授权该目录内选定安装包的临时 content URI，不暴露路线与账号文件。更新源配置保存在独立 SharedPreferences 中。

## 本地生成正式发布产物

现有用户签名证书 SHA-256 为：

```text
3b2756ff4b3920fac9738a20d5d2ad6900a4e5c4f774edea62a1ffc049ba8190
```

当前对应本机现有 androiddebugkey。为保持已安装用户的覆盖兼容，发布过程必须使用对应的同一私钥；不要让 CI 临时生成新的 debug.keystore，也不要直接换另一把 release key。

Gradle 的 release 签名从环境变量读取，缺失时拒绝生成可发布的 release 包：

| 环境变量 | 内容 |
| --- | --- |
| `KEGENT_KEYSTORE_FILE` | 固定兼容密钥库的绝对路径 |
| `KEGENT_STORE_PASSWORD` | 密钥库口令 |
| `KEGENT_KEY_ALIAS` | 签名私钥别名 |
| `KEGENT_KEY_PASSWORD` | 私钥口令 |

配置这些环境变量后运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
node --test scripts/create-update-release.test.mjs
node scripts/create-update-release.mjs `
  --apk app/build/outputs/apk/release/app-release.apk `
  --out app/build/outputs/updates `
  --tag v2.0.0 `
  --base-url https://github.com/Haven-mao/kegent/releases/download/v2.0.0 `
  --notes-file docs/releases/v2.0.0.md `
  --sdk D:/Dev/Android/sdk
```

脚本用 aapt 和 apksigner 检查真实产物，然后生成：

- `kegent-2.0.0.apk`：与旧安装兼容签名的非 debuggable 安装包。
- `update.json`：从产物读取的包名、版本、最低系统，实际大小与 SHA-256，以及更新说明。
- `kegent-2.0.0.apk.sha256`：供独立核对。

脚本拒绝包名、签名和 tag 不一致的产物；默认拒绝 debuggable APK。`--allow-debug` 仅用于本地检查，不用于正式发布工作流。脚本支持 Android build-tools 36/37 的签名输出格式。

## GitHub 发布工作流

新增 `.github/workflows/release.yml`，在推送版本 tag 或手动指定已存在的 tag 时运行。普通 main push、PR 构建不会创建更新 Release。

原有 `android.yml` 调整为只构建 debug、单测、Lint 和发布脚本测试，避免 PR/普通分支构建依赖签名私钥。正式 release 构建只在已配置签名环境变量时执行。

首次使用，需要在仓库 Settings → Secrets and variables → Actions 配置：

| Secret | 内容 |
| --- | --- |
| `KEGENT_KEYSTORE_BASE64` | 固定兼容密钥库文件的 Base64 内容 |
| `KEGENT_STORE_PASSWORD` | 密钥库口令 |
| `KEGENT_KEY_ALIAS` | 私钥别名 |
| `KEGENT_KEY_PASSWORD` | 私钥口令 |

不要把私钥或其 Base64 提交到代码仓库。本次没有上传或配置远程 Secrets。

每次发布：

1. 增加 Gradle 中的 versionCode，修改 versionName，编写 `docs/releases/vX.Y.Z.md`。
2. 提交对应代码，创建与 versionName 匹配的 `vX.Y.Z` tag；推送该 tag 后工作流开始。也可手动运行指定已经存在的 tag。
3. 工作流构建和测试，使用固定证书签名，生成并验证 APK 与清单。
4. 先创建草稿 Release，上传 APK、清单和摘要，再发布草稿。已经公开的同名 Release 不会被自动覆盖。
5. 已安装含更新模块版本的用户可在应用中检查到新 versionCode。

如果未来有镜像，用同一批 APK 与清单，保持 APK 摘要和签名，不重新签包。APK 下载 URL 必须实际存在；生成本地清单不等于已经上线。

## 已执行的验证

本轮验证包括清单字段与版本边界、HTTPS 与重定向、摘要校验、下载暂停及 Range 续传、错误响应；发布脚本核验错误签名/包名/tag/debuggable 标志。

独立 `.updatetest` 应用使用真实 APK，验证正确签名接受、其他签名拒绝、包名拒绝、当前版本拒绝、FileProvider 路径隔离、恢复已校验更新及取消安装。还实际调用了系统安装器，将测试包 versionCode 11 覆盖到 12，并在安装后确认路线库条目与 SharedPreferences 标记均保留。

测试应用与生产 `com.haven.kegent` 分开，不会用测试候选包覆盖用户安装。详细输出在 `app/build/reports/app-update-tests/`。远程 GitHub 工作流需要仓库 Secrets、代码/tag 推送和正式 Release 才能验证线上检查到下载的完整链路。
