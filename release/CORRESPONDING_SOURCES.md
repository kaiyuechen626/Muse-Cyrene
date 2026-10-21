# 对应源码与依赖来源

0.2.0 的源码 ZIP 必须与同一候选 APK 一起保留，并记录各自 SHA-256。源码边界由 `tools/release/source_set.py` 和 `release/source-baseline.json` 定义；不通过公开私钥、个人配置或未知二进制提供所谓“完整源码”。

- Muse 上游：[v2.6.1](https://github.com/Zer0Qing/Muse/tree/v2.6.1)，原作者及版权按 `UPSTREAM_PROVENANCE.txt`、LICENSE、NOTICE 保留。
- Cyrene 修改：本源码包内的当前业务代码、构建脚本、运行时输入锁、修改记录及内置网页资源；与已验收 0.1.9 的业务源文件逐一核对。
- sherpa-onnx：[v1.13.8](https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.8)。语音 AAR 的官方 URL 与摘要固定在 `tools/release/fetch-voice-runtime.py`，不打入源码 ZIP。
- eSpeak NG：sherpa-onnx 的[构建配置](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/cmake/espeak-ng-for-piper.cmake)固定 [Piper fork ed530aa](https://github.com/csukuangfj/espeak-ng/tree/ed530aa113046142eb5115cf2fc9157854d0ffe1)。发布含该运行库的二进制时仍需提供适用的对应源码与构建材料；仅有一个链接不代表本项目已完成全部分发义务。
- Node/npm/Python：17 个固定输入、发布者证据和本项目组装脚本在 `tools/runtime-inputs`、`tools/runtime-pack`、`tools/python-pack`。上游输入摘要/身份已固定，但其全部原生依赖的精确构建来源与许可材料仍须按实际分发包核对。
- Maven/Gradle 依赖：版本解析结果见 `release/release-dependencies.json`，该图包含项目/平台等组件，不等于 357 个独立分发二进制，也不是许可授权清单。
- ZipVoice/Vocos 模型和用户角色：本候选不分发语音克隆模型或参考录音。用户自行导入模型/录音的权利与应用源码许可分别处理。

在 Junrar、ML Kit、原生运行时对应源码与最终候选验收项关闭之前，不把该本地候选标为正式 APK。源码可审查不等于所有依赖可按同一条件分发。
