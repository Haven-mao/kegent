# KmAo 源码恢复记录

2026-10-18，从蓝叠已安装的 KmAo 1.0（versionCode 1，约 8.2 MB）核对并恢复。APK 和修改前源码压缩包保存在本次 Codex 工作区的 work/kmao-recovery，未提交个人数据或安装包到 Git。

恢复策略是保留仍然存在的可读 Java 源码，对照反编译结果补回差异。三个业务类的主要流程、Vincenty 距离计算、路线插值与模拟定位逻辑在原目录中已经存在。没有将 JADX 的反编译错误占位代码覆盖到项目里，也不声称从 APK 恢复了丢失的 Git 历史、原始注释或签名私钥。

已确认并恢复的资源：

- 原应用名称 KmAo；按用户后续要求，新构建显示名与交付文件改为 `kegent`、`kegent.apk`。
- 原浅蓝主题色 #DAF0FB；本次扩展了对应的深色语义色。
- `ka_squ.jpg` 原图标素材。来源为用户本机已安装 APK，原始文件 SHA-256 为 `f4191971b267fa75727bc6e2d51ddcd727cedd05a0088b3a69c876636f7cc6ed`。
- `dutpj_mutipoint.kml` 默认路线，191 个点，实机计算约 400.04 m；SHA-256 为 `33efb4cf3bc477083e633cbd17253bc14c37901e7e53843aa2a315b76b49337a`。
- 图标素材追加了来源元数据，解码后的全部像素保持一致；当前打包文件摘要见 `ASSET_PROVENANCE.json`。
- 原项目的 224 点 `three_km.kml` 仍保留为已有资源。

按用户后续要求，所有活动源码、namespace、applicationId、Manifest 组件引用与测试包统一迁移到 **com.haven.kegent**。由于包名已经改变，安装新构建会与蓝叠中的旧 KmAo 并存。原安装包的签名和本机 debug 签名不一致，不能从 APK 还原签名私钥。

原目录没有 Git 元数据，本次建立本地 main，并先提交原始源码快照，随后提交恢复与开发结果。没有配置或推送远端。
