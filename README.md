# V Network Lab v0.1

Android 原生网络诊断工具，Kotlin，无 AI Key，无后台服务器。最低 Android 8.0（API 26）。

## 功能
- 当前网络类型、系统互联网验证状态、网络接口、本地 IP、DNS 服务器
- 5 次 HTTPS 请求往返延迟、请求成功次数、相邻样本抖动估计（非 ICMP Ping，非严格丢包率）
- 域名系统 DNS 查询
- 打开 Android 网络设置
- 午夜极光深蓝渐变主题

## 构建
将整个 `V-Network-Lab` 目录内容上传到 GitHub 仓库根目录，Actions 自动运行 `Build V Network Lab APK`，下载 artifact `V-Network-Lab-debug-apk`。

## 发布提醒
Actions 生成的 APK 是 debug 签名，仅用于测试。正式发布到 V Hub 前，应设置稳定的 release 签名，保存好密钥，使用 GitHub Releases 发布正式 APK，并计算 SHA-256 写入目录。`applicationId` 固定为 `com.vapplications.network`，`versionCode=1`，`versionName=0.1.0`。

HTTPS 测试目标 `https://www.google.com/generate_204` 可能在部分网络不可达；失败不一定代表设备断网。Wi-Fi SSID 等敏感网络标识未请求，也不显示。
