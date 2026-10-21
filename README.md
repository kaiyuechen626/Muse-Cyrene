# Muse Cyrene

基于 [Muse v2.6.1](https://github.com/Zer0Qing/Muse/tree/v2.6.1) 的非官方 Android 修改版，由 kaiyuechen626 维护。保留原作者 Mozi、上游版权、LICENSE 与 NOTICE，不由上游维护者发布或背书。Cyrene 修改按 GPL-3.0-or-later 提供。

## 下载与安装

[**直接下载 Muse Cyrene 0.2.0 APK（ARM64）**](https://github.com/kaiyuechen626/Muse-Cyrene/releases/download/cyrene-v0.2.0/Muse-Cyrene-0.2.0-arm64-v8a.apk) · [发行说明及全部附件](https://github.com/kaiyuechen626/Muse-Cyrene/releases/tag/cyrene-v0.2.0) · [旧版 0.1.0](https://github.com/kaiyuechen626/Muse-Cyrene/releases/tag/cyrene-v0.1.0)

Android 8.0 以上 ARM64 手机可用，面向 Galaxy S25。下载 APK 后点击安装。包名 `io.zer0.muse.cyrene`，版本 `0.2.0` / `271`；与已发布 0.1.0（261）及已验收 0.1.9（270）使用同一签名，具备保留数据覆盖升级条件。建议先备份；遇到签名冲突不要卸载旧版。

## 本次更新

以 0.1.0 整理基线为基础，保留已验收 0.1.9 的功能。保留 DeepSeek 对话及原有工具、内置网页和 Node/Python 功能；新增手机本地音色朗读，不重写对话请求逻辑。

- 导入自己有权使用的 `.cyvoice` 音色包，选择角色，在手机上生成语音。
- DeepSeek 回复完成后可自动朗读；应用内离线角色模式不把语音文本发送给云端 TTS。
- 提供可关闭的快速合成，按句分组，播放时预生成后续片段，减轻等待。
- 保留自动朗读开关、离开聊天后继续朗读、暂停、继续、停止及系统 TTS 选择。
- 应用内模式不要求安装 Cyrene Voice、CloneTTS、Termux 或额外兼容工具。

在“设置 → 媒体／语音播报 → Cyrene 离线角色音色”导入音色包并选择角色，再按需打开自动朗读和后台续读。APK 不附语音模型、参考录音、个人音色、API 密钥或免费服务额度。现有已导入音色无需重新导入；新用户需准备有权使用的完整音色包。

**整个应用并非离线应用**：DeepSeek 对话和其他在线功能按用户配置联网；原有其他语音方式仍按所选服务工作。应用内本地角色的合成与播放在手机进行。

## 验证与已知限制

本聊天对应的 Galaxy S25 验收已确认 0.1.9 保留原聊天、原音色、朗读、暂停／继续和返回桌面续读；0.2.0 保持其业务代码，复用这些功能验收结果。最终 0.2.0 文件未再次安装到手机，不将既有记录改写成一次新的安装。构建、27 项选定测试、签名、资源、JNI 回调及升级条件核对通过。见 [发布核对记录](release/PUBLICATION.md) 与 [机器记录](release/verification.json)。未声称全量测试或所有设备验收。

长文及首次合成仍可能等待；后台续读不保证所有后台新回复自动开始。蓝牙、通话打断、全应用回归和 x86_64 真机不在本次已验收范围。历史 NDJSON 导入限制保持原状。继承的 `libimage_processing_util_jni.so` 不满足 16KB ELF 对齐，不能宣称兼容全部 16KB 页面设备；APK 的 ZIP 对齐检查通过不能替代这一限制。

## 源码、构建与许可

见 [构建说明](BUILDING_CYRENE.md)、[0.2.0 发行说明](releases/0.2.0.md)、[发布材料说明](release/PUBLICATION.md) 和 [许可核对](CYRENE_LICENSE_REVIEW.md)。Git 源码与对应源码 ZIP 不包含私人签名、聊天、录音、音色模型或语音 AAR；构建前从官方固定版本取得并校验依赖。Node/Python 的原验证门禁保留。

0.1.0 的 LICENSE、NOTICE 和第三方资料随 0.2.0 继续保留，另附新增语音许可与对应上游源码位置。Junrar / ML Kit 组合许可疑问及完整运行时对应源码核对没有被本次发布标记为解决；延续 0.1.0 的披露，不代表获得 Google、FSF 或上游的全面分发许可确认。模型和录音权利需另行具备。参见 [隐私与网络说明](docs/RELEASE_PRIVACY.md)。
