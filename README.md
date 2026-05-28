# AudioBooks

离线听书 Android App。目标是把本地书籍导入到 App 私有项目目录，解析成可朗读文本，使用本地 TTS 模型播放，并保存听书进度。

## 当前功能

- 导入 EPUB 文件，复制到 App 私有项目目录。
- 解析 EPUB spine 中的 XHTML/HTML 文本。
- 按中英文标点拆句，较长句会在逗号等软停顿处分段。
- 书架页和听书页分离，手机竖屏下正文区域不再被压缩。
- 播放/暂停、上一句/下一句。
- 0.5x 到 3.0x 语速控制。
- 句子或分段之间加入短暂停顿，避免连续无断句播放。
- 支持 Sherpa-ONNX 中文 VITS 音色模型；模型不提交到 Git，也不随 APK 打包。
- 首次播放前在 App 内提示下载公开模型。
- 本地 JSON 保存书籍解析结果和播放进度。
- 允许联网下载公开模型；不上传书籍、播放记录或设备信息。
- 用户数据统一保存在 App 私有 `audiobooks-data/` 目录下，包含 `books/`、`models/`、`downloads/`。

## 本机构建

本仓库包含项目内 Android SDK/JDK/Gradle 工具链配置，使用脚本即可：

```powershell
.\scripts\build-debug.ps1
```

首次构建会通过 `scripts\setup-models.ps1` 下载编译必需的 Sherpa-ONNX AAR 到本地。AAR 和模型文件体积较大，不提交进 Git。

生成的 APK：

```text
app\build\outputs\apk\debug\app-debug.apk
```

连接手机并打开 USB 调试后安装：

```powershell
.\scripts\install-debug.ps1
```

## 首版取舍

首版先做 EPUB。PDF、MOBI/AZW3、OCR、音频缓存、更高质量 TTS 模型选择和后台 MediaSession 都保留为后续切片。

当前 TTS 后端使用 Sherpa-ONNX `vits-icefall-zh-aishell3`。模型下载链接内置在代码中，App 会在缺少模型时提示用户下载：

```text
https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-icefall-zh-aishell3.tar.bz2
```

隐私边界是“不上传用户书籍、播放记录或设备信息”。公开模型下载是允许的。Debug APK 只打 `arm64-v8a`，适配当前测试手机；如要支持模拟器或 x86 设备，需要调整 `app/build.gradle.kts` 的 ABI 过滤。

