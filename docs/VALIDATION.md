# 本地验证记录

## 2026-11-10：1.4.0 原生真实地图与路线编辑

- `:app:exportDebugApk`、`:app:testDebugUnitTest`、`:app:lintDebug` 通过；54 项 JVM 测试，0 失败；Lint 0 错误、71 项告警，保留现有及非阻断告警，不能描述为零告警构建。
- 常规安装包为 `app/build/outputs/apk/kegent/kegent.apk`，应用 ID `com.haven.kegent`，versionCode 7，versionName `1.4.0-dev`，包含 arm64-v8a、armeabi-v7a、x86、x86_64。
- 最终常规包已在当前蓝叠保留数据覆盖安装并打开，安装前确认无定位服务正在运行，备份了原 APK。已再次目视确认实际 `com.haven.kegent` 主页面显示真实底图及 400.04 米 / 191 点的默认 DUTPJ 路线；独立 `.maptest` 测试包及其测试 APK 已移除。
- 在蓝叠 / Android 9 / x86_64 上，用临时 Gradle init 文件构建独立 `com.haven.kegent.maptest` 与 `.maptest.test` 包；仅运行 `RouteEditorSmokeTest`，1 项界面回归通过。测试覆盖主页面真实底图、默认路线、操场参数/道次/方向、保存返回及重开、连续三点手动平滑与保存。未运行既有卸载用户应用的完整 connected 测试流程。
- 已目视核对主页面、操场、手动三张截图：真实底图显示 DUTPJ 校区道路、建筑、水系及地名；原始默认路线 400.04 米 / 191 点在操场范围内，方向与操场一致。基于原始路线拟合的可编辑操场约 400.29 米，与原始 KML 为两条不同的几何。
- 早期 407.64 米截图来自第二道测试模板，初始化误用了起点附近位置且朝向未校准；已修正为按符合跑道形状的闭合路线拟合中心、长轴、半径与直道，并补充不依赖起点的中心/拟合回归。测试夹具每次恢复原始 DUTPJ，不再拿上次测试模板冒充默认路线。
- 主页面与编辑页共用 MapLibre 真实地图，删除原有装饰街区绘制；瓦片失败保留轨迹并明确提示。路线结果与草稿分别保存，页面和后台服务统一读取原子选择记录；普通 KML 导入和默认恢复已接入相同仓库。
- 公共 Valhalla 步行服务在开发电脑请求成功，返回 `pedestrian` 路段与 polyline6；客户端解码、路段未完成/断口/过大吸附阻止应用、缓存失效及手绘连接均有 JVM 覆盖。未将电脑接口成功表述为真实用户道路场景已全面验收。
- 截图与界面测试输出保存在 `app/build/reports/map-editor/`，使用方式和默认测试服务限制见 `docs/MAP_ROUTE_EDITOR.md`。本轮不创建 Keep 运动记录或主动交接，目标区域道路覆盖及现场跑道校准继续由实际使用反馈验证。

## 2026-11-10：Keep 本地记录目录自动初始化

- 已安装 Keep 8.7.30 / 49337 的反编译代码确认：`iw1.b` (`LogFileStorage`) 与 `iw1.c` (`LogFileV2Storage`) 在获取记录文件路径时按需创建 `files/local_logs`，该目录不是连接检查的既定前提。
- 使用当前已提交源码及本次修复的独立副本构建，避免混入工作区中同时进行的地图编辑器改动。`:app:testDebugUnitTest`、`:app:lintDebug`、`:app:exportDebugApk` 通过；29 项 JVM 测试，0 失败；Lint 0 错误、53 项告警。
- 蓝叠 5 / Android 9 上通过独立 `app_process` 运行生产环境的 `KeepLogDirectory` 和 `RootShell`，10 项目录回归通过：缺失目录创建及 UID/GID/0700 校验、已有目录属性修复且保留子文件内容和属性、重复调用、初始化失败提示及重试、普通文件冲突、目录符号链接、悬空符号链接、父目录缺失、带空格/引号路径、非法 UID/GID 拒绝。
- 目录回归仅操作 `/data/local/tmp/kegent-keep-directory-*` 下的临时夹具，使用实际 Keep UID/GID 设置属主；结束后已清理夹具和测试运行包，没有安装或卸载应用，也没有创建 Keep 运动记录或写入其数据库。
- 蓝叠测试环境的 SELinux 为 Disabled；`restorecon` 调用已执行，但 SELinux Enforcing 真机上的标签效果及真实云端上传不属于本次验证结果。

## 2026-10-19 验证记录

日期：2026-10-19。当前来源修复版本 1.3.1-dev；下文另列 1.3.0 及更早版本的历史验证。测试环境：蓝叠 Pie64 / Android 9；构建环境：JDK 21、Gradle 8.14.3、AGP 8.13.0、Android SDK 36。

## 1.3.1 来源修复验证

- `:app:testDebugUnitTest`、`:app:exportDebugApk`、`:app:lintDebug` 通过：29 项 JVM 测试，0 失败；Lint 0 错误、59 项告警。
- 当前包名 `com.haven.kegent`，versionCode 6，versionName `1.3.1-dev`。
- 1.3.1 APK 已通过 `adb install -r` 安装到蓝叠；PackageManager 回读为 versionCode 6 / 1.3.1-dev。安装包 SHA-256：`09a5c26758a558b3722b70e18d5b8a2fad26fee083b08ff990f5616f72f5cfb0`。
- 已安装 Keep 的原生 `OutdoorVendor` 经 Gson 序列化为 `{"genre":"KeepApp","source":"Keep"}`；`SummarySourceType.OFFLINE.h()` 返回 `OFFLINE`。这纠正了上一版把枚举名称和序列化值混为一谈、把摘要改为小写的错误。
- 旧的 `KEEP / KEEP_APP` 被 Keep 解析为 null，再次序列化得到 `{}`；修复后的生成记录经 Keep 本地 Gson 和上传 Gson (`ow.k.d()`) 均保留正确 vendor。验证仅使用内存对象，未调用上传接口或创建运动记录。
- 已上传列表读取服务器的 `ThirdPartyInfo.name/icon`；待上传页固定显示“Keep App 离线数据”，不能用它作为云端徽标验证。用户已确认上一版上传后仍无来源标识；本次修复尚未新增云端记录验证。
- 测试基准为 `app/src/test/resources/keep-source-contract-8.7.30.json`，内容来自已安装客户端的实际返回值。
- 更正上一轮测试清理声明：2026-10-19 05:06:35 至 05:11:35 的 1.76 公里临时记录，Keep 日志于 05:14:50 记录了同 ID 的 `upload success`。删除本地队列不能删除云端副本，因此不能继续声称该测试“未上传、完全清理”。
- 本次已按原始 fixture 内容哈希确认并备份、移除该临时本地详情文件；本地队列为 0 条，云端副本未删除。运行时检查的调试进程、端口转发及临时 IPv4/IPv6 规则均已清理。

## 1.3.0 历史构建与标识

- `:app:exportDebugApk`、`:app:assembleDebugAndroidTest`、`:app:testDebugUnitTest`、`:app:lintDebug` 通过。
- 最终安装包：`app/build/outputs/apk/kegent/kegent.apk`，显示名 `kegent`，应用 ID `com.haven.kegent`，versionCode 5，versionName `1.3.0-dev`。
- 28 项 JVM 测试通过，0 失败；Lint 0 错误、60 项告警。新增覆盖秒级时间/旧设置迁移、自然配速首公里和后段分段行为、积分反演、暂停调速、轨迹速度一致性和地图坐标转换。告警仍保留，不能将其描述为零告警构建。
- 1.3.0 原交付记录曾报告 APK 已安装；之后 `connectedDebugAndroidTest` 清理会卸载目标包，故不能以该报告推断当前安装状态。活动源码与配置不再使用旧包标识。

## 设备行为

- 3 项 Android 测试通过：包标识、SQLite 摘要结构/重复保护、实际模拟定位服务生命周期。
- 定位服务验证包含 GPS 位置和速度、暂停、继续及结束后清理。
- UI 检查覆盖 1920×1080 横屏、600×1100 手机布局、深色主题、1.3 倍字体，以及运行中切到历史页、暂停和结束；本轮补充了圆角图标、顶部 tabs、昼夜开关和在线地图失败回退。
- 运行中的历史页继续显示距离、时间、进度和暂停/结束按钮；禁用的历史主按钮显示“结束后补跑”。
- 轨迹使用可中断的 220 ms 过渡；系统动画关闭时立即更新。状态过渡与 Material 进度也遵循系统设置。
- 1.2.0 独立 UI 审查检查了 14 张截图，最终结论为 `ship`，无待修复项。本轮在蓝叠上重新检查了浅色、深色和窄屏布局；在线瓦片在当前设备网络不可达时正确回退到离线底图，真实 OSM 瓦片显示需在可联网设备复核。
- 明暗主题下，按钮文字对比度至少 6.14:1，控件边界至少 4.78:1，弹窗操作文字至少 6.90:1；禁用文字至少 4.90:1。浅蓝主按钮填充继续保留。
- Keep 地图复现：点击 Street 缩略图后，全屏页显示道路/水域/地名；返回详情页底图同步恢复，Keep 强制停止并重新打开记录后仍保持。点击 Street 文字区域本身不会作为成功证据。
- 旧版来源测试只断言写出的字面值，没有检查 Keep 解析后的 vendor；这不足以发现 `KEEP / KEEP_APP` 被解析为 null。当前结论以 1.3.1 的实际解析结果为准。

## Keep 本地交接

- 在 Keep 8.7.30 / 49337 验证一条隔离测试记录：本地详情和摘要写入、Keep 待上传列表显示、Keep 自身详情解析器读取，未出现解析失败日志。
- 测试期间同时隔离 Keep 的 IPv4/IPv6 网络。没有验证真实云端写入或历史记录接受规则。
- 后续 1.76 公里临时记录只证明待上传列表有固定提示和详情可打开；该记录实际自动上传，详见上方更正，不属于这里的隔离测试结论。
- 1.2.0 的变化配速和转换后的坐标也通过 Keep 本地解析测试，测试仍在双栈网络隔离下执行。
- 测试结束已删除唯一测试记录；摘要队列恢复到测试前的 0 条，两个网络隔离规则已移除。
- 普通 `connectedDebugAndroidTest` 不会启用 Keep 写入测试。实际定位测试和 Keep 交接测试均需显式参数；后者还需要宿主网络隔离。

## 资源与交付边界

- 图片来源有记录，11 个主资源栅格的解码像素在补充来源元数据后保持一致。
- JPEG 使用内嵌来源；WebP 使用相邻 JSON 来源文件。`prepareAppResources` 生成供 Android 编译的资源目录，排除这些 JSON，安装包不含作为 drawable 打包的来源文件。
- 调试 APK 使用本机 debug 签名；原 APK 的签名私钥、原始注释和丢失的 Git 历史无法恢复。
- 新包可与原 KmAo 共存。蓝叠的画面尺寸、密度、夜间模式、字体缩放已恢复；交付时无运行中的定位服务。
- Keep 升级需要重新适配私有数据结构；本地交接通过不代表云端接受。未 Root 设备可预览并导出 GPX。
