# DailyQuest 可行性分析与环境检查报告

日期：2026-10-18
分析对象：MAA/FGA 风格的多手游自动化助手（Android）

---

## 1. 结论（先说答案）

**可以做出来。** 你描述的需求比 MAA 简单得多（无 ADB 通信、无复杂战斗逻辑，主要是"启动游戏 → 识图 → 点击 → 退回退出 → 下一个"），技术路线完全成熟，且有 FGA 这个开源先例证明"无障碍 + 识图"方案在真机上稳定可用。

**但当前电脑环境不具备构建条件**：JDK、Android SDK、Gradle 均未安装（2026-10-18 实测）。好消息是**不需要装 Android Studio**，只需要命令行工具链（JDK 17 + Android command line tools），全部可以用轻量方式安装，甚至可以让 GitHub Actions 在云端帮你编译，电脑上一个字节都不装。

---

## 2. 需求拆解与技术选型

| 需求 | 技术方案 | 可行性 |
|---|---|---|
| 屏幕截图（识图的输入） | MediaProjection API（系统弹窗授权一次） | ✅ 成熟 |
| 图像识别找按钮 | OpenCV 模板匹配（OpenCV Android SDK 纯 Java 集成） | ✅ FGA 同款方案 |
| 文字识别（OCR，可选/二期） | Google ML Kit Text Recognition，离线端侧、免费、支持中日英 | ✅ 成熟 |
| 模拟点击/滑动/退回键 | AccessibilityService（无障碍手势 dispatchGesture / GLOBAL_ACTION_BACK） | ✅ 无需 root、无需 USB 调试 |
| 悬浮窗（控制面板/状态） | SYSTEM_ALERT_WINDOW + 前台服务 | ✅ 成熟 |
| 启动/切换游戏 | PackageManager 按包名 launch intent；退回键退出 | ✅ 成熟 |
| 任务队列（多游戏串行） | 自建状态机执行器（Kotlin 协程） | ✅ 架构问题，非技术风险 |
| 自定义模板/预设（FGA 式录制） | 应用内截屏裁剪 + JSON 脚本引擎 | ✅ 核心功能，见 §4 |
| 定时任务（可选/二期） | AlarmManager + 前台服务唤醒 | ✅ 可做（MAA 手机版用 Shizuku 是为了熄屏拉起，我们可以先不做） |

**开发语言**：Kotlin + Jetpack Compose（UI）。不装 Python、不装 Android Studio，均不影响。

### 关于"不能开 USB 调试"
完全没影响。我们根本不需要 ADB：
- 调试期：APK 通过文件传输/扫码/蓝叠共享文件夹安装；日志用应用内日志页 + 写文件。
- 运行期：截图走 MediaProjection，点击走无障碍，全部是用户态权限，与 FGA 一致。

---

## 3. 关键风险与对策

1. **MediaProjection 授权**：Android 10+ 每次冷启动服务都要弹一次"是否允许录屏"。对策：点"开始任务"时弹一次，整个任务队列共享一次授权（FGA 也是这样）。这是系统限制，无法绕过，但可接受。
2. **不同分辨率适配**（蓝叠 vs 真机 1220×2712）：所有模板匹配按"比例缩放 + 多尺度匹配"，录入模板时记录设备分辨率，运行时自动缩放。
3. **游戏更新导致 UI 变化**：这是所有识图类工具的宿命。对策：模板全部用户可自定义/可重录，任务脚本 JSON 化，游戏更新后只需重录一张图，不用改代码。
4. **无障碍服务被系统杀掉**：国产 ROM 常见。对策：前台服务 + 通知 + 电池优化白名单引导（MAA 手机版同款做法）。
5. **日系手游检测**：有些游戏检测 root/调试器，但无障碍服务和录屏是系统合法功能，FGA 能跑扭曲仙境就证明这条路通。

---

## 4. 架构设计（草案）

```
app/
 ├─ core/                  # 与 UI 无关的核心引擎
 │   ├─ capture/           # MediaProjection 截屏
 │   ├─ vision/            # OpenCV 模板匹配、（二期 OCR）
 │   ├─ input/             # 无障碍手势封装（点/滑/返回/等待）
 │   └─ script/            # 脚本引擎：JSON → 步骤状态机
 ├─ service/               # 前台服务 + 悬浮窗 + 任务执行器(协程)
 ├─ data/                  # 游戏配置、任务队列、模板库（Room/JSON 文件）
 └─ ui/                    # Compose：首页/任务编辑/模板录入/日志/设置
```

### 脚本引擎（核心中的核心）
参考 FGA 的"自己录图追加操作"，任务 = 有序步骤列表，每步 JSON 描述：

```json
{
  "game": "twisted-wonderland-jp",
  "tasks": [
    { "name": "每日签到", "steps": [
      { "action": "wait_image", "template": "home_icon.png", "timeout": 60 },
      { "action": "tap_image",  "template": "login_bonus.png" },
      { "action": "tap_text",   "text": "閉じる", "fallback_tap": [0.5, 0.9] },
      { "action": "back", "times": 2 }
    ]}
  ]
}
```

- 通用动作原语：`wait_image` / `tap_image` / `tap_text`(OCR) / `tap_at` / `swipe` / `back` / `wait` / `repeat_until_gone` / `launch_game` / `exit_game`
- 所有游戏、所有任务都由这些原语拼成 → 新游戏 = 录图 + 拼 JSON，**不写代码**。
- 应用内提供"录制模式"：悬浮窗上点"录这里"，自动截屏、框选裁剪、保存为模板，追加到当前任务的步骤里。

### UI 框架（参考 MAA 手机版，简化）
- 首页：服务状态（无障碍/悬浮窗/录屏授权自检）+ 分辨率/版本信息
- 任务页：游戏列表（扭曲仙境日/美、FGO 日/美、明日方舟…）→ 每游戏勾选任务 → "开始任务"按队列串行执行
- 模板/脚本页：模板库管理 + 录制入口
- 日志页：执行过程实时日志 + 失败时自动截图存档
- 设置页：权限引导、电池白名单、（二期）定时任务

顶部"游戏画面预览窗"（截图里那个灰色区域）是可选的——技术上就是把最近一帧截图显示出来，成本很低，调试时很有用，建议做。

---

## 5. 构建环境方案（三选一，可组合）

| 方案 | 电脑上装什么 | 说明 |
|---|---|---|
| **A. GitHub Actions 云端构建（推荐起步）** | 什么都不装 | 仓库里放 `.github/workflows/build.yml`，push 后云端自动出 APK，在 Releases 下载。你有 GitHub 账号即可。 |
| **B. 本地命令行构建** | JDK 17（zip 解压版，~190MB）+ Android cmdline-tools（~150MB，SDK 按需下载共 ~1-2GB） | 不装 Android Studio。我可以全部帮你配好，以后构建就是一行 `gradlew.bat assembleDebug`。 |
| **C. AIDE / 手机端构建** | 不装 | 不推荐，折腾。 |

建议：**A + B 都配**。日常由 Actions 出包，本地留一套工具链应急和跑快速验证。JDK 用 Microsoft OpenJDK 或 Temurin 的 zip 版，解压即用，不污染系统。

---

## 6. AI 协作模式（我 + DeepSeek + 你）

推荐流程：

1. **这个仓库就是唯一事实来源**。代码 + `docs/` 文档都在这里。
2. 仓库根目录维护一份 `AGENTS.md`（AI 通用约定文件），写清：项目结构、构建命令、编码规范、当前进度、下一步任务。任何 AI 进项目先读它。
3. 和我协作：直接在这个对话里说要做什么，我改代码、commit。
4. 和 DeepSeek 协作：把 `AGENTS.md` + 相关文件贴给它（或用它的仓库读取功能），让它按文档里的任务清单干活；产出以 PR 或直接贴 diff 的形式回来，我（或它）负责合并。
5. 进度同步靠 `docs/ROADMAP.md`（任务清单 + 勾选状态），谁完成谁打勾。
6. Bug 协作：日志页导出的日志/截图放进 `issues/` 目录或 GitHub Issue，任何 AI 都能据此定位。

这样不会出现"两个 AI 各说各话"：文档是共享记忆，git 历史是共享时间线。

---

## 7. 建议的里程碑

- **M0 环境就绪**：GitHub Actions 能编出空白 APK（Hello World + Compose 骨架）→ 手机能装能跑
- **M1 核心能力**：无障碍点击 + MediaProjection 截屏 + OpenCV 模板匹配 demo（识别一张图并点击它）
- **M2 脚本引擎**：JSON 任务执行器 + 前台服务 + 悬浮窗控制（开始/停止）
- **M3 第一个游戏全流程**：选一个最简单的（建议扭曲仙境签到），跑通"启动→签到→退回退出"
- **M4 多游戏队列 + 任务勾选 UI**：MAA 式列表
- **M5 录制模式 + 模板库**：FGA 式自定义
- **M6+ 二期**：OCR、定时任务、蓝叠适配优化

M0~M2 是地基，也是我接下来可以立刻开工的部分。
