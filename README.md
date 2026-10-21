# Muse Cyrene

## 下载 Android 安装包

### [⬇ 直接下载 Muse Cyrene 0.1.0 APK（ARM64）](https://github.com/kaiyuechen626/Muse-Cyrene/releases/download/cyrene-v0.1.0/Muse-Cyrene-0.1.0-arm64-v8a.apk)

[发布详情、源码与校验值](https://github.com/kaiyuechen626/Muse-Cyrene/releases/tag/cyrene-v0.1.0) · [安装说明](https://github.com/kaiyuechen626/Muse-Cyrene/releases/download/cyrene-v0.1.0/INSTALL.txt)

适用于 **Android 8.0 及以上的 ARM64 安卓设备**。下载后打开 `.apk` 安装；网页上的 `Code → Download ZIP` 是源码，不能直接安装。

本版支持自动朗读、离开聊天续读、后台播放和暂停／继续。首次使用需自行配置聊天模型及 TTS，应用不包含语音包、API Key 或免费额度。已安装 0.1.2 的用户请勿降级安装。

这是非原创、非官方修改版。第三方组合分发许可疑问仍未解决，具体说明见发布详情。

**非原创项目 · 基于 Muse 的非官方修改版 · 0.1.0 自动朗读版**

本项目是在 [Zer0Qing/Muse](https://github.com/Zer0Qing/Muse) v2.6.1 基础上的二次修改，不声明对上游代码的原创所有权。原项目版权、LICENSE 和 NOTICE 均予以保留；本项目的修改见 [CYRENE_MODIFICATIONS.md](CYRENE_MODIFICATIONS.md)，不由上游维护者发布或背书。

0.1.0 APK 已发布；其准确对应源码请使用 Release 附件或 `cyrene-v0.1.0` 标签，首页 `main` 分支保留早期开发源码。4 个来源授权范围待确认的历史测试包未包含在公开副本中；相关 6 个测试明确标记为跳过，ZIP/7z 测试保留。应用功能代码未删减。详见 [源码状态](SOURCE_PUBLICATION_STATUS.txt) 和 [测试资源来源](TEST_FIXTURE_PROVENANCE.txt)。

Muse Cyrene 是基于 [Muse v2.6.1](https://github.com/Zer0Qing/Muse/tree/v2.6.1) 的**非官方修改版 Android 应用**，不是 Muse 原生插件，也不由上游维护者发布或背书。已发布 v0.1.0 APK，下载入口见本页顶部。

目标：打字发送消息 → AI 回复完成 → 使用用户配置的 TTS 自动朗读。应用不包含语音包、Fish Audio API Key 或免费额度，也不附带公共中转服务。

## 已实测与使用

0.1.0 发布 APK 已在 Samsung SM-S9310 / Android 16 上验收：保留数据升级、AI 回复自动朗读、离开聊天续读、后台及短时锁屏播放、应用内和通知栏暂停／继续。验收范围不代表所有设备、TTS 引擎或全部功能。

在「设置 → 语音播报」打开「自动朗读 AI 回复」，如需离开聊天继续播放，再开启「离开聊天后继续朗读」。配置自己的 TTS 引擎、凭据和有权使用的 Voice ID。应用内播放控件及播放通知提供暂停、继续、停止。

本应用沿用 Muse 的聊天及模型供应商配置；聊天模型与 TTS 引擎分别配置。独立包名为 `io.zer0.muse.cyrene`（必须使用构建参数 `-PcyreneBuild=true`），与原版使用独立数据空间。

## 行为与已知限制

- 自动朗读在当前前台普通会话的一轮回复成功完成后触发，读整条回复，不是逐字同步播报。
- 后台续读用于保持已经开始的朗读；不承诺回复在后台完成时自动开始播放。系统强制停止、省电限制或音频焦点变化仍可能中断。
- 正在朗读或暂停时到来的新回复不会排队或插播；切换会话、连续回复、暂停跨句、电话/蓝牙及异常网络仍待专项测试。
- 已实测暂停/继续基于 Fish Audio 云端音频；不承诺所有系统 TTS 引擎具有相同能力。语音对话、群聊、Agent 和无障碍辅助组件不在本次验证范围。
- 原版 NDJSON 备份的批量导入可能因父子记录插入顺序出现 SQLite 外键错误（787），源码中的导入问题尚未修复。先保留原版及备份；不要把含聊天或凭据的备份上传到仓库。此前为单个用户转换备份成功，不代表通用导入修复。TTS 凭据需在新应用中重新配置。

## 构建、验证与发布

见 [BUILDING_CYRENE.md](BUILDING_CYRENE.md)、[CYRENE_RELEASE_CHECKLIST.md](CYRENE_RELEASE_CHECKLIST.md) 及 [CYRENE_CHANGES.txt](CYRENE_CHANGES.txt)。旧的外部 build_muse.cmd 不属于本仓库，也不是复现构建的必要条件。不要直接使用尚未适配 Cyrene 的上游发布工作流。

## 可选 Cloudflare Worker

Worker 是独立的 HTTP 中转服务，**本发布副本不包含其源码或部署脚本，也不会部署它**。如需要，请自行审查和部署兼容 Fish Audio 的服务，并在应用中配置自己的地址，例如 `https://YOUR-WORKER.YOUR-SUBDOMAIN.workers.dev`。这是占位示例，不是可用服务；没有默认公共 Worker。Muse 的 Fish 请求会追加 /tts，需按实际服务路由配置。模型可用性、费用和使用权限由服务商决定。

## 来源和许可

上游标签 v2.6.1，提交 `d7ce76b9640e42afcb571fa3813c9bcf0779969c`。Cyrene v0.1.0 是修改版自己的版本号，不表示上游 Muse 降级。

保留 [LICENSE](LICENSE) 的 GPL 正文、[NOTICE](NOTICE) 的 GPL v3 或更新版本授权与第三方说明。修改记录及整理日期（2026-10-18）见 [CYRENE_MODIFICATIONS.md](CYRENE_MODIFICATIONS.md)。新增 Cyrene 代码按 GPL-3.0-or-later 提供。依赖和素材仍受各自许可约束，素材再分发及完整依赖清单尚待确认；不把上游声明视为逐项完成的法律审查。参见 [CYRENE_LICENSE_REVIEW.md](CYRENE_LICENSE_REVIEW.md)。

正式分发 APK 前，须提供同一 Cyrene 标签/提交的完整对应源码、构建说明与发布校验清单。上游的捐赠、更新、反馈入口属于上游，不代表 Cyrene 服务；反馈请前往 [本仓库 Issues](https://github.com/kaiyuechen626/Muse-Cyrene/issues)。
