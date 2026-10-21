> 状态更新（2026-10-21）：以下保留构建或核对时的历史记录；0.1.0 已发布，所有者已要求上传 0.2.0。已有验收复用与当前状态见 [release/PUBLICATION.md](release/PUBLICATION.md)。原未解决项及未执行测试未改写为通过。

> 历史 0.1.9 记录。当前 0.2.0 请使用 BUILDING_CYRENE.md、release/verification.json 与 tools/release/verify_source.py；下述路径和旧核验命令仅描述原整理版本。

# Muse Cyrene 基础版整理

以已验收的 0.1.0 源码提交 `0b94f4ab9c96a87419a53a739c8d12d0425f8535` 为基础，只合入当前需要的本地语音功能。安装版本使用 0.1.9（270），便于覆盖现有版本并保留手机数据；没有回退 Android 版本代码。

实际开发、构建目录为 `D:\muse update\Muse-Cyrene-working`。用户指定的 `D:\muse update\Muse-Cyrene-0.1.0-consolidated` 自收到保护指示后不再修改；已停止原先在该目录进行的构建，从中复制源码到当前工作目录。保护指示之前，该目录已移入语音改动，因此纯 0.1.0 基线另以原始提交及其归档逐文件核对。复制前后的源码哈希检查点保存在工程外的 `work/consolidation-010/protected-backup-checkpoint.json`。

保留 0.1.0 的原有功能，包括 DeepSeek、聊天与设置、OCR、二维码、压缩包、网页界面及 Node/Python 运行时。另保留原“昔涟”应用内音色、音色导入、可关闭的快速合成、短句合并、播放时预生成后续片段、系统 TTS 回退、自动朗读、后台朗读及暂停／继续／停止。

不带入后续二维码替换实验、OCR/RAR 原型、相关候选依赖和实验报告、旧迭代 APK 或旧构建缓存。必要许可证、署名和运行时校验保留。音色模型和手机聊天数据不打入源码。

`docs/consolidation/source-manifest.json` 记录逐文件基线与保留文件的 SHA256。2488 个基线文件保持原样，7 个基线文件承载语音改动及打包检查，19 个新增文件为语音实现、依赖与测试。发现旧迭代包缺少 0.1.0 内置网页资源，已从核验过的 0.1.0 APK 恢复 7 个资源文件，并增加缺失时阻止构建的检查。

## 验证

27 项选定测试已通过：0.1.0 自动朗读门控 2 项、语音协调器 4 项、保留的本地朗读与设置兼容 3 项、原生回调 2 项、句子分组 5 项、预生成 5 项、请求隔离 6 项。Release 构建通过，用时 21 分 7 秒，保留运行时签名／版本检查、R8 和既有基线的 lintVital。结构核对不等于全应用回归。

最终 ARM64 APK 核对通过：恢复的 7 个网页资源和另外 222 个基线静态资源逐字节一致，2 个运行时压缩包与 0.1.0 一致，83 个基线原生库存在，2 个编译性能配置重新生成且存在，原生语音回调签名正确。安装包签名与原应用一致，非调试 Release，16KB ZIP 对齐检查通过。

0.1.9 已在 Galaxy S25 覆盖安装并成功启动，首次安装时间未变。用户在这次重建版上确认原聊天、原音色、朗读、暂停／继续及返回桌面继续播放都正常。阶段日志记录原音色快速模式首段约 7.1 秒开始，两段播放器切换约 19 毫秒，第二段在第一段播完前生成完成。这是同一条回复的实测，不是所有文本的延迟保证。

尚未执行全应用逐功能手动回归、x86_64 真机验证，或本轮重新调用真实 DeepSeek API 并验证新回复的自动朗读。自动朗读门控和排队调用由选定单元测试覆盖。详细机器可读记录见 `docs/consolidation/verification.json`。

新目录的首次并行编译因 JVM 内存不足而失败，随后停止并以单个构建 worker、6GB 临时 JVM 堆重试；没有修改基线 `gradle.properties`。构建日志在工程外的 `work/consolidation-010/working-build.log` 和 `working-build-serial.log`。

原始源码包 `muse First edition/Muse-Cyrene-0.1.0-device-test/Muse-Cyrene-0.1.0-source.zip` 的 2495 个文件已全部核对，等同于上述原始提交；SHA256 为 `b2cc141843f43836867af36b18e4c982ec87dd2ceb238aeb39aa3582245a3bc2`。这个原始归档未修改。

运行 `python tools/consolidation/verify_rebuild.py` 核对源码。构建后加 `--apk 安装包绝对路径` 核对内置资源；JNI 回调、签名、安装版本和手机验证另外记录。

运行 `python tools/consolidation/verify_native_callback.py 安装包绝对路径` 可核对最终压缩包中 JNI 要求的回调方法。

构建沿用 `BUILDING_CYRENE.md` 的环境与运行时验证要求。个人签名配置 `keystore.properties`、SDK/JDK、已验证的双架构运行时生成目录属于本地构建输入，不进入公开源码包。内置网页编译资源已随整理后的源码包提供，可从原网页源码重新生成。

本机首次完整构建使用串行和临时 6GB JVM 堆；这些是构建进程参数，不修改备份或基线配置：

```powershell
.\gradlew.bat :app:assembleRelease -PcyreneBuild=true -PversionName=0.1.9 -PversionCode=270 "-PcyreneRuntimePython=本机验证器Python路径" "-Dorg.gradle.jvmargs=-Xmx6g -Dfile.encoding=UTF-8 -XX:+UseParallelGC" -Dorg.gradle.parallel=false --max-workers=1 --no-configuration-cache --offline --no-daemon
```
