# Muse Cyrene 0.3.0-lan.2 构建说明

本版包名 `io.zer0.muse.cyrene`，版本代码 273，发行目标 ARM64。构建脚本仅生成本地产物，不自动推送或创建 Release。

## 环境与依赖

JDK 21、Android SDK / Build Tools 36、NDK 27.0.12077973、CMake 3.22.1、项目 Gradle Wrapper。设置 JAVA_HOME、ANDROID_HOME 或不提交的 local.properties。Python 3.11+ 用于源码校验；固定 Sigstore 依赖与运行时证据见 tools/runtime-inputs/README.md。

源码保留必需内置网页产物。重建网页时使用 Node.js 22，在 web 中运行 npm ci、npm run build，并核对资源，不得缺失网页后仍打包。

语音 AAR 不进入公开源码。运行 `python tools/release/fetch-voice-runtime.py`，再用 `--verify-only` 核对官方固定版本和 SHA-256。Node/npm/Python 双架构输入、证据、组装和验证按 tools/runtime-inputs 完成；Gradle 的完整运行时及逐文件摘要门禁保持启用。生成的运行时不进入 Git。

## 签名与构建

覆盖升级须使用现有合法持有的同一签名私钥，在不提交的 keystore.properties 中配置 storeFile、storePassword、keyAlias、keyPassword。新开发者使用自己的本地签名不能覆盖其他人的原签名安装；不要自动卸载或清除数据。主应用及无障碍 Provider 必须同签名。

通过已验证的 Python 环境执行以下命令；`cyreneRuntimePython` 使用你自己的固定依赖环境。当前已验收 APK 含发布目标的测试所需保留规则，非 debuggable。

```powershell
.\gradlew.bat :app:packageRelease :app:assembleReleaseAndroidTest :app:assembleRelease `
  -PlanSyncTests=true -PcyreneBuild=true -PversionName=0.3.0-lan.2 -PversionCode=273 `
  -PnoUniversal -PcyreneRuntimePython=python `
  '-Dorg.gradle.jvmargs=-Xmx6g -Dfile.encoding=UTF-8 -XX:+UseParallelGC' `
  -Dorg.gradle.parallel=false --max-workers=1 --no-configuration-cache --no-daemon
```

已有缓存时可加 --offline。本次最终 APK 构建和 lintVital 通过，未宣称全量 Lint 或全应用验收。

## 校验与导出

`python tools/release/verify_source.py` 核对本版源码基线及公开边界；`python tools/release/export_source.py --output <新的ZIP路径>` 生成对应源码与逐文件摘要。旧 0.1.9/0.2.0 基线原样保留，本版单独记录当前已验收业务源码。

`python tools/release/verify_apk.py --apk <APK路径> --sdk <SDK路径> --report <JSON路径>` 核对版本、包名、签名、资源、Provider、ZIP 对齐，并继续报告继承的 ELF 16KB 限制。手机测试类为 io.zer0.muse.sync.LanSyncDeviceTest，协议测试需要可销毁电脑服务与一次性配对 URI，不要用正式角色作测试。

prepare-local.ps1 仍是仅本地构建工具，无推送或发布功能。历史语音单元测试与本次 LAN 真机结果分开记录。当前验收见 release/LAN_SYNC_VALIDATION.json；旧版历史见 release/PUBLICATION.md。
