# Muse Cyrene

基于 [Muse v2.6.1](https://github.com/Zer0Qing/Muse/tree/v2.6.1) 的非官方 Android 修改版。感谢 Mozi 与上游贡献者；保留 GPL-3.0-or-later、LICENSE、NOTICE 和来源记录。

## 0.3.0-chatrooms.1 预发布版

面向 ARM64 Android 设备，包名 `io.zer0.muse.cyrene`，versionCode **275**。配套电脑项目为 [Muse Multi-Agent](https://github.com/kaiyuechen626/Muse-Multi-Agent)，建议同时使用 0.3.0-chatrooms.1。[下载本版 ARM64 APK](https://github.com/kaiyuechen626/Muse-Cyrene/releases/download/cyrene-v0.3.0-chatrooms.1/Muse-Cyrene-0.3.0-chatrooms.1-arm64-v8a.apk) · [发行附件](https://github.com/kaiyuechen626/Muse-Cyrene/releases/tag/cyrene-v0.3.0-chatrooms.1)。

- 角色聊天、各角色人格与长期记忆、本地角色语音及原有工具功能。
- 独立群聊入口，2–8 位角色保留各自人格和记忆，显示发言者身份。
- 局域网二维码或 USB 首次配对，自动同步角色、单聊、事实记忆与兼容聊天室的公开文字。
- 手机私信、附件、共享文档和群聊专用上下文不会转换为电脑公开发言。范围见 [聊天室说明](docs/CHAT_ROOMS.md)。

## 安装和升级

在本仓库 Releases 选择本版 `arm64-v8a.apk`；源码 ZIP 不是安装包。仅从自己信任的发布者取得 APK，并核对附件 SHA-256。覆盖升级需要原安装与新 APK 同签名；先导出完整备份，不要卸载旧版或清除数据。自行构建使用自己的签名，不能覆盖其他签名的安装。

手机入口：设置 → 数据与备份 → 局域网同步。电脑服务需要保持运行，设备须能互通；二维码无需 USB 调试，USB 首次配对需要调试授权。系统省电策略可能限制后台同步。

模型与工具服务需自行配置；APK 不附个人音色、模型、录音、账号或免费 API 额度。可用 [Cyrene Voice Studio](https://github.com/kaiyuechen626/Cyrene-Voice-Studio) 制作自己有权使用的音色包。

## 开发与验证

[构建说明](BUILDING_CYRENE.md) · [本版发行说明](releases/0.3.0-chatrooms.1.md) · [验证记录](release/CURRENT_VALIDATION.json) · [贡献指南](CONTRIBUTING.md) · [发布步骤](docs/PUBLISHING.md)

源码包含当前新增的聊天室协议、测试和多语言资源；依赖和运行时须按固定哈希获取。执行 `python -B tools/release/verify_source.py` 检查源码边界，`python -B tools/release/export_source.py --output <新ZIP路径>` 导出源码。CI 进行源码边界和网页检查，完整 APK 构建还需要运行时准备及本地签名。

## 许可和已知限制

[隐私与网络](docs/RELEASE_PRIVACY.md) · [许可核对](CYRENE_LICENSE_REVIEW.md) · [历史来源](UPSTREAM_PROVENANCE.txt)

继承的 Junrar / ML Kit 组合许可疑问、完整运行时对应源码核对及图像库 ELF 16KB 限制仍保留披露，未在本次整理中解决。ZIP 16KB 对齐通过不代表所有 ELF 均兼容 16KB 页面。当前是预发布材料；不宣称全应用回归或所有设备验收。
