# ROADMAP

图例： [ ] 未开始  [~] 进行中  [x] 完成

## M0 环境就绪
- [ ] Gradle 项目骨架（Kotlin + Compose 空壳，无 Android Studio 可构建）
- [ ] GitHub Actions 构建 workflow，产出 debug APK 到 Artifacts/Releases
- [ ] （可选）本地工具链：JDK 17 zip 版 + Android cmdline-tools，文档记录安装步骤
- [ ] 真机安装跑通 Hello World

## M1 核心能力验证
- [ ] 无障碍服务：点击/滑动/全局返回 封装 + 权限引导页
- [ ] MediaProjection 截屏：授权一次、循环取帧
- [ ] OpenCV 集成 + 模板匹配 demo（悬浮窗按钮触发：找图→点击→显示命中坐标）

## M2 脚本引擎
- [ ] 动作原语：wait_image/tap_image/tap_at/swipe/back/wait/launch_game/exit_game
- [ ] JSON 任务解析与协程状态机执行器（可暂停/停止/单步超时）
- [ ] 前台服务 + 悬浮窗控制面板（开始/停止/当前步骤显示）

## M3 首个游戏全流程（扭曲仙境 日服 签到）
- [ ] 模板录制工具初版（截屏框选保存）
- [ ] 签到任务脚本 + 真机跑通"启动→签到→退出"

## M4 多游戏队列与主 UI
- [ ] 游戏列表 + 任务勾选 + 串行队列执行
- [ ] 首页状态自检（权限/分辨率/服务状态）、日志页（失败自动截图）

## M5 自定义与扩展性
- [ ] 完整录制模式（FGA 式：录图→追加步骤）
- [ ] 游戏/任务/模板的导入导出（JSON 分享）

## M6+ 二期（评估后再排）
- [ ] OCR（ML Kit）与 tap_text 原语
- [ ] 定时任务（AlarmManager；熄屏拉起是否需要 Shizuku 再评估）
- [ ] 账号切换、运行时长限制、熄屏挂机等快捷选项
