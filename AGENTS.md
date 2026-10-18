# AGENTS.md — DailyQuest 项目协作约定

> 任何 AI（Codex / DeepSeek / 其他）接手本项目前，先读完本文件和 `docs/FEASIBILITY.md`。
> 用户是无 Android 开发经验的新手，所有说明要能看懂，构建命令必须可直接复制执行。

## 项目一句话
MAA/FGA 风格的多手游自动化助手：无障碍手势点击 + MediaProjection 截屏 + OpenCV 模板匹配，
通过 JSON 脚本驱动"启动游戏 → 识图点击完成任务 → 退回退出 → 下一个游戏"的串行队列。

## 硬性约束
- **不使用 USB 调试 / ADB**（日系手游检测闪退）。真机调试 = 安装 APK + 应用内日志。
- 不依赖 root、不依赖 Shizuku（二期定时任务再评估）。
- 目标设备：真机（1220×2712）与蓝叠模拟器，模板匹配须做分辨率缩放适配。
- 用户环境：Windows，无 Android Studio、无 Python。构建走命令行 Gradle 或 GitHub Actions。

## 技术栈
- Kotlin + Jetpack Compose，minSdk 26+（暂定），单模块 app
- OpenCV Android SDK（模板匹配）；ML Kit Text Recognition（二期 OCR）
- Room 或纯 JSON 文件存配置/模板索引（未定，M2 前决定）

## 目录约定
- `docs/` 设计与决策文档（FEASIBILITY.md / ROADMAP.md）
- `app/` Android 模块（M0 时创建）
- `issues/` 真机 bug 的日志与截图存档（按日期建子目录）

## 构建
- 云端：push 到 main 后 GitHub Actions 出 debug APK（workflow 在 `.github/workflows/`）
- 本地（配好后）：`gradlew.bat assembleDebug`，产物在 `app/build/outputs/apk/debug/`

## 协作流程
1. 改动前先读 `docs/ROADMAP.md` 确认当前里程碑，不要越级做后面的功能。
2. 完成功能后更新 ROADMAP 勾选状态，重要决策补进 `docs/`。
3. Commit message 用中文，格式：`[模块] 做了什么`（如 `[vision] 接入 OpenCV 模板匹配`）。
4. 不引入需要 Python/Node/ADB 的开发期依赖。

## 当前状态
- [x] 可行性分析（docs/FEASIBILITY.md）
- [ ] M0：项目骨架 + GitHub Actions 构建出可安装 APK
- 下一步：M0。等待用户确认后开始搭骨架。
