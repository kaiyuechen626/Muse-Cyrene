> 状态更新（2026-10-21）：以下保留构建或核对时的历史记录；0.1.0 已发布，所有者已要求上传 0.2.0。已有验收复用与当前状态见 [release/PUBLICATION.md](release/PUBLICATION.md)。原未解决项及未执行测试未改写为通过。

# 0.2.0 许可与来源核对

日期：2026-10-21。原 Muse 版权 Mozi、GPL v3 或更新版本授权、上游来源与修改说明均保留。Cyrene 修改按 GPL-3.0-or-later；第三方许可不被本项目许可取代。程序化替换图形仍按 `ASSET_PROVENANCE.txt` 记录来源。

## 本轮补齐

本地语音 AAR 固定官方 sherpa-onnx 1.13.8，摘要为 `b22c3fc1b6a45666d28892bb2f7694beeb77a8362d7ebd77c1a5431ec9435471`。公开源码不分发 AAR，使用 `tools/release/fetch-voice-runtime.py` 单独获取。已加入 sherpa-onnx、ZipVoice、eSpeak NG、ONNX Runtime、Vocos 的上游许可文本，并在应用许可清单中补充本地语音说明。APK 不附语音克隆模型或个人音色；模型权利不能由代码许可推定。

依赖图在 `release/release-dependencies.json`，来自当前 releaseRuntimeClasspath；它是解析结果，不是每个二进制许可的完整审计。原 Node/Python 输入验证和已保留的许可文本继续有效；发布者身份和摘要不代表构建可复现或许可完备。

## 仍阻止正式 APK 分发的事项

1. [Junrar 8.1.1 许可证](https://github.com/junrar/junrar/blob/v8.1.1/LICENSE) 含禁止用于重建 RAR 压缩算法的附加限制。已经保存精确版本的全文，但与 GPL 组合分发的兼容依据仍未建立。不能把它笼统归类为普通 Apache/MIT 依赖；需要有效依据/例外或另行审查的技术方案。
2. [ML Kit 条款](https://developers.google.com/ml-kit/terms) 适用于现有 OCR/条码依赖。模型及软件条款、指标告知、传递依赖和 GPL 组合分发依据仍需核实。保留其原有功能，不将其改称 Apache-2.0 或把“端侧识别”描述为“绝不联网”。
3. 本地语音原生库、Node/npm/Python 及其依赖，需要对应实际 APK 的完整版权/许可清单和适用对应源码安排。现有 npm 子依赖及 CPython 许可有保留，不宣称其已覆盖全部组件。

以上并非本轮新增的功能问题，也适用于任何包含相同依赖的 0.1.0 APK。**可以先审查/发布不含这些二进制的应用源码；不要据此直接认定正式 APK 已获准分发。** 实际发布前应关闭相应事项，不能用改文档代替权利依据。
