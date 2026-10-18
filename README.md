# kegent

Android 路线与跑步配置工具，应用 ID 与源码包名统一为 `com.haven.kegent`。

## 实时跑步

选择内置的 DUTPJ 400 米路线或导入 KML，配置速度、路线次数、更新间隔、海拔和定位精度。轨迹预览来自真实 KML 坐标，配速与总距离即时计算。运行由前台服务负责，支持暂停、继续、调速、结束和通知栏操作。开放路线到终点后折返，闭合路线循环运行。

默认启用「自然配速」：超过 1 km 的跑步前段略快，在第一公里内平滑过渡，后段保持小幅连续变化。输入速度作为平均速度，运行时显示当前配速；关闭开关可恢复匀速。暂停不计入有效时长，调整速度不会重置路线进度。

主题保留已安装 KmAo 的浅蓝色 `#DAF0FB`，支持深色模式、窄屏与横屏；深色页面背景为 `#121212`。顶部提供无闪屏的昼夜切换按钮，页面切换和状态反馈使用原生 Android 动效，并遵循系统动画开关。

在真机上使用实时定位前，需要授予位置权限并在开发者选项中选择 kegent 作为模拟定位应用。模拟定位是否被其他应用接收取决于其自身行为。

## 单条历史补跑

在「历史补跑」页选择精确到秒的过去起止时间、路线和平均配速，并填写用于步数与热量估算的步频、体重。旧的分钟级设置会自动补 `:00`。先预览生成结果，再决定导出 GPX，或检查本机 Keep 后进行交接。自然配速对每条记录保持稳定，反复预览、导出和交接使用同一曲线，最终时间和总距离不变。交接记录同时写入 `source=keep_app` 和 `source_page=keep_app`，让 Keep 历史列表显示与实时记录一致的“Keep App”来源标识。

当前本地接入适配 **Keep 8.7.30 / 49337**，需要设备已提供 Root 和 sqlite3。确认后备份本地索引、写入待同步详情与摘要，直接打开 Keep 的待上传列表，由用户核对并使用 Keep 本身的上传操作。界面只报告本地交接状态，最终云端结果以 Keep 为准。普通未 Root 设备仍可预览和导出 GPX。

详见 [接入机制与边界](docs/KEEP_INTEGRATION.md)。

## 地图底图

Keep 的「隐藏地图」或 Gotham 图层会显示纯色/深色背景与轨迹。打开记录的「全屏 → 图层」，**点击 Street 上方的地图缩略图**（不要只点下面的文字），再点确认即可显示真实底图。本次已在蓝叠实际切换、重启 Keep 后复核；地图样式由 Keep 管理。

导入路线应为标准 WGS84/GPS KML。路线预览会按 WGS84 的 Web Mercator 投影异步加载 OpenStreetMap 街区瓦片，并在应用缓存中复用；无网络时回退到不误导地理位置的街区风格底图。预览保留 OSM 署名，不承诺与 Keep 的腾讯图层样式或瓦片数据一致。历史交接按 Keep 的腾讯定位链路转换为 GCJ-02；实时模拟定位和 GPX 继续使用 GPS 坐标，避免重复转换。已有上传记录的坐标不会被本次版本自动改写。

## 编译和验证

需要 JDK 17 或更新版本、Android SDK 36。项目使用 Gradle 8.14.3 与 Android Gradle Plugin 8.13.0，minSdk 26、targetSdk 34。

配置本机 `local.properties` 的 `sdk.dir` 后执行：

```text
./gradlew :app:exportDebugApk :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Windows 使用 `gradlew.bat`。应用显示名与交付安装包名分别为 `kegent` 和 `kegent.apk`。`exportDebugApk` 生成的 debug APK 位于 `app/build/outputs/apk/kegent/kegent.apk`；包名同为 `com.haven.kegent`，与旧安装包可并存。

常规设备测试只运行内存 SQLite 兼容性测试等，不写入 Keep 数据。`KeepHandoffIntegrationTest` 默认跳过；只有显式参数和宿主网络隔离同时存在时才允许本地交接验证。不要在未隔离网络的环境启用该测试。

## 源码恢复与来源

[恢复说明](docs/RECOVERY.md)记录从已安装 APK 找回的名称、配色、图标和默认路线，以及不能从 APK 恢复的内容。保留原项目路径计算的可读源码基础，新增模块拆分为 `core`、`ui`、`keep`。所有栅格素材的来源与摘要在 [素材记录](docs/ASSET_PROVENANCE.json)。

原项目基于 [LangQi99/dlut-FakeRun](https://github.com/LangQi99/dlut-FakeRun)。
