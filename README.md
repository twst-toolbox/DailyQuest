# DailyQuest

MAA / FGA 风格的多手游自动化助手（无障碍 + 识图点击）。

## 当前状态

- **M0 骨架完成**：空白 Compose 应用，可编译安装
- 下一步：M1 无障碍服务 + MediaProjection 截屏 + OpenCV 模板匹配

## 构建方式（无需 Android Studio）

### 方式一：GitHub Actions（推荐，零安装）

1. 在 GitHub 上创建空白仓库 `DailyQuest`
2. 本地执行：
   ```bash
   git remote add origin https://github.com/twst-toolbox/DailyQuest.git
   git branch -M main
   git push -u origin main
   ```
3. 等 2 分钟，在 Actions 或 Releases 下载 APK

### 方式二：本地命令行（Windows）

需要：JDK 17（zip 解压版）+ Android SDK Command Line Tools

```bash
# 设置环境变量（假设解压到 D:\Android）
set JAVA_HOME=D:\Android\jdk-17
set ANDROID_HOME=D:\Android\sdk

# 构建
gradlew.bat assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 技术栈

- Kotlin + Jetpack Compose
- 无障碍服务（点击/手势）
- MediaProjection（截屏）
- OpenCV（模板匹配）
- 无 ADB / 无 root / 无 Python

## 文档

- [可行性分析](docs/FEASIBILITY.md)
- [扭曲仙境任务流](docs/GAMES/twisted-wonderland-jp.md)
- [路线图](docs/ROADMAP.md)
- [AI 协作约定](AGENTS.md)
