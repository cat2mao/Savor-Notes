# 清欢小谱 · Savor Notes（Android）

离线优先的个人菜谱记录 App。支持图片菜谱、食材与标签搜索、可附图步骤、注意事项、多个抖音参考链接、做菜次数、AI 草稿、加密模型密钥，以及手动备份和智能合并恢复。

## 开发环境

- Android Studio，JDK 17
- Android SDK Platform 36、Build Tools 36.0.0
- Gradle 8.11.1、Android Gradle Plugin 8.10.1
- 最低 Android 10（API 29），目标 Android 16（API 36）

Gradle Wrapper、Google Maven 和 Maven Central 已配置为阿里云国内镜像。若工程位于含中文字符的目录中，可再生成的构建输出会自动放到 `%USERPROFILE%\.gradle\project-builds\savor-notes`，避免 Windows 测试进程损坏中文类路径。

## 构建与验证

```powershell
.\gradlew.bat test assembleDebug lintDebug
```

构建后的安装包会复制到 `artifacts/SavorNotes-debug.apk`。

## 数据与外部服务

菜谱和图片保存在应用本地私有目录。系统相册、相机和文件选择器分别用于导入图片、拍摄图片和导出/恢复备份。AI API Key 使用 Android 加密存储且不进入备份。

抖音功能保存原始分享链接，并尽力读取公开标题和封面；点击时优先唤起抖音，失败则交给浏览器。应用不会下载视频，也不自动读取抖音收藏夹。
