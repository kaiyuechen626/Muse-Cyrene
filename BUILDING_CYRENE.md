> 状态更新（2026-10-21）：以下保留构建或核对时的历史记录；0.1.0 已发布，所有者已要求上传 0.2.0。已有验收复用与当前状态见 [release/PUBLICATION.md](release/PUBLICATION.md)。原未解决项及未执行测试未改写为通过。

# Muse Cyrene 0.2.0 本地候选构建

本说明取代早期 0.1.0 准备说明。`REBUILD_STATUS.md` 及 `docs/consolidation/verification.json` 是此前 0.1.9 的历史证据；当前候选结果以 `release/verification.json` 为准。

## 环境和输入

需要 JDK 21、Android SDK 36、Build Tools 36.0.0、NDK 27.0.12077973、CMake 3.22.1 及项目自带 Gradle Wrapper。用 `JAVA_HOME`、`ANDROID_HOME` 或不提交的 `local.properties` 配置本机路径。Python 3.11+ 用于源码/发布校验；运行时验证需要 `tools/runtime-inputs/README.md` 所述固定 Sigstore 依赖和验证环境。

源码中提供经过校验恢复的内置网页产物。重新生成时在 `web` 目录执行 `npm ci` 与 `npm run build`（Node.js 22/npm），并核对网页资源。不得在缺失网页客户端时产出缩水 APK。

语音 AAR 不进入公开源码包。先执行：

```powershell
python tools/release/fetch-voice-runtime.py
python tools/release/fetch-voice-runtime.py --verify-only
```

它仅获取官方 sherpa-onnx v1.13.8 静态 ONNX Runtime AAR，并固定 SHA-256；不能用未知二进制替代。

Node/npm/Python 仍要求完整双架构运行时。17 个输入记录及证据位于 `tools/runtime-inputs`，按该目录说明验证和组装，然后运行 `runtime_bundle.py verify`。所有 Gradle 打包入口继续验证输入、生成状态与逐文件摘要，不能通过禁用门禁、复制未知运行库或借用失败输出完成构建。生成的 `app/build/cyrene-runtime` 不属于公开源码。

## 签名及版本

使用现有合法持有的同一私钥，在根目录创建不提交的 `keystore.properties`。不要输出密码，不要为了覆盖安装而换签名或卸载手机应用。参数名为 storeFile、storePassword、keyAlias、keyPassword。主应用与无障碍 Provider 必须同签名。

本候选固定 `0.2.0` / `271`，包名 `io.zer0.muse.cyrene`。发布前必须以实际已发布的 0.1.0 为准核对包名、签名与递增关系；若其版本代码已达到 271，应提高 0.2.0 的编号后重建，不能降低已发布编号。版本配置在 `tools/release/release-profile.json`，上游构建默认值保留，不代表 Cyrene 的正式版本。

本地候选沿用现有个人证书，以保持当前安装版的升级兼容；这并不替代对公开版本签名策略和私钥保管的确认。未来不得为了更换签名而自动卸载或清除数据。

```powershell
.\tools\release\prepare-local.ps1 -Python python3 -RuntimeVerifierPython "你的已验证 Python 环境" -Offline
```

此脚本只生成本地候选、依赖清单和选定 JUnit 测试结果，不上传、不推送、不安装，也不会创建 Release。首次建立缓存时可以不传 `-Offline`。

测试只覆盖指定的 7 个纯 JUnit/MockK 套件；测试专用 init 脚本排除未用到的 Robolectric 原生运行库，不能用于宣称 Robolectric 或真机测试通过。Release 保留 R8、资源压缩、签名门禁和 lintVital；不宣称全套 Lint 或全量测试已完成。

## 产物检查

只将目标 ARM64 Release APK 纳入本轮候选。检查版本、包名、debuggable=false、主包/Provider 签名、16 KB ZIP 对齐、本地语音回调及内置网页/运行时资源。源码与最终 APK 分别生成 SHA-256，保存匹配的源码清单及依赖图。APK 不包含用户模型、录音或个人角色。

本机生成的候选不等于已批准分发。按 `CYRENE_RELEASE_CHECKLIST.md` 处理未关闭项目；本轮不监测或定时发布；等用户确认 0.1.0 已发布后，再继续 0.2.0 的发布流程。
