# Muse Cyrene

基于 [Muse v2.6.1](https://github.com/Zer0Qing/Muse/tree/v2.6.1) 的非官方 Android 修改版，由 kaiyuechen626 维护。感谢 Mozi 及上游贡献者，保留原版权、LICENSE、NOTICE 与 GPL-3.0-or-later。

## 0.3.0-lan.2 局域网同步预发布版

[下载本版 ARM64 APK](https://github.com/kaiyuechen626/Muse-Cyrene/releases/download/cyrene-v0.3.0-lan.2/Muse-Cyrene-0.3.0-lan.2-arm64-v8a.apk) · [本版发行附件](https://github.com/kaiyuechen626/Muse-Cyrene/releases/tag/cyrene-v0.3.0-lan.2)

这是与电脑端配套的局域网同步预发布版。本版面向 ARM64 Android 设备，版本代码 273，包名 `io.zer0.muse.cyrene`。配套电脑端为 Muse Multi-Agent 0.3.0-lan.2，需要 Node.js 24+。

- 电脑选择二维码或 USB 首次配对，手机确认后，同一局域网内自动同步角色、文字聊天与事实记忆。
- 发现独立创建的同名 AI 时询问合并或分别保留，任一端选择一次即可；分别保留记住选择。
- 确认合并后统一身份并汇总聊天、记忆；电脑已有设定优先，不同设定保留副本，原始资料留存备份。
- 密钥、模型服务配置、DSH 授权及本地语音模型留在各自设备，不随局域网同步传输。

安装同签名 APK 可覆盖升级，不要卸载旧版或清除数据。升级前可先导出自己的完整备份。首次 USB 配对需要 Android USB 调试并由用户允许电脑；二维码配对无需 USB 调试。配对后无需继续接数据线。

手机入口：设置 → 数据与备份 → 局域网同步。电脑服务须保持运行，网络需允许设备互通。后台任务可能受省电与系统调度影响；强制停止后需要再次打开。完整范围见 [局域网同步](docs/LAN_SYNC.md)。

## 验证与既有功能

当前 APK 已在 Samsung S25 覆盖安装，版本与签名核对通过，首次安装时间保持不变。5 项隔离真机测试及 1 项 Wi-Fi 直连测试通过，覆盖加密、二维码、原生资料回写、同名身份归并、记忆去重与电脑回传。正式同名角色由用户自行选择，测试未自动确认。

保留原有聊天、工具及本地语音功能；本轮未重跑全应用、蓝牙、通话、x86_64 真机或历史全部语音测试。旧版验收只作为历史基线，不改写为本版新验收。详见 [本版验证](release/LAN_SYNC_VALIDATION.json) 和 [本版发行说明](releases/0.3.0-lan.2.md)。

## 制作本地音色

[Cyrene Voice Studio](https://github.com/kaiyuechen626/Cyrene-Voice-Studio) 可制作供 Muse Cyrene 导入的音色包：先导入基础模型，再用自己有权使用的参考录音及准确原文创建角色，导出完整 `.cyvoice` 包。Studio 不附基础模型或预置音色；导入后使用 Muse 应用内的离线角色音色，无需 Studio 在后台运行。

## 源码与许可

[构建说明](BUILDING_CYRENE.md) · [隐私与网络](docs/RELEASE_PRIVACY.md) · [许可核对](CYRENE_LICENSE_REVIEW.md) · [旧版发行记录](release/PUBLICATION.md)。

源码包保留完整应用源码、固定依赖证据、许可和必需网页资源，排除私钥、个人聊天、配对状态、模型、录音、缓存和语音 AAR。运行时按固定版本与哈希获取，原有验证门禁保留。

继承的 Junrar / ML Kit 组合许可疑问、完整运行时对应源码核对及图像库 16KB ELF 限制未由本轮解决，沿用已有披露，不宣称兼容全部 16KB 页面设备。APK 不含个人音色、语音模型、录音或免费服务额度；整款应用并非离线应用。
