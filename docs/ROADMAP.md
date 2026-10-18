# ROADMAP

图例： [ ] 未开始  [~] 进行中  [x] 完成

## M0 环境就绪
- [x] Gradle 项目骨架（Kotlin + Compose 空壳，无 Android Studio 可构建）
- [x] GitHub Actions 构建 workflow，产出 debug APK 到 Artifacts/Releases
- [ ] （可选）本地工具链：JDK 17 zip 版 + Android cmdline-tools，文档记录安装步骤
- [x] 蓝叠模拟器安装跑通 Hello World

## M1 核心能力验证
- [x] 无障碍服务：点击/滑动/全局返回 封装 + 权限引导页（蓝叠实测通过）
- [x] MediaProjection 截屏：授权、取帧（蓝叠实测通过）
- [x] OpenCV 集成 + 模板匹配引擎（多尺度 + 分辨率自适应 + 区域限定）——待真机验证

## M2 脚本引擎
- [x] 动作原语：wait_image/tap_image/tap_at/swipe/back/wait/launch_game/exit_game/close_until/repeat_until
- [x] JSON 任务解析与协程状态机执行器（可停止/超时/日志回调）
- [ ] 前台服务 + 悬浮窗控制面板（开始/停止/当前步骤显示）——下一批
- [ ] 失败自动截图存档——下一批

## M3 首个游戏全流程（扭曲仙境 日服 上课清体力）
- [ ] 模板录制工具（截屏框选保存）——关键！用户自己录图
- [ ] 内置扭曲仙境初始脚本（启动→签到→上课循环→退出）
- [ ] 蓝叠实测跑通完整流程

## M4 多游戏队列与主 UI
- [ ] 游戏列表 + 任务勾选 + 串行队列执行
- [ ] 首页状态自检（权限/分辨率/服务状态）、日志页

## M5 自定义与扩展性
- [ ] 完整录制模式（FGA 式：录图→追加步骤）
- [ ] 游戏/任务/模板的导入导出（JSON 分享）

## M6+ 二期（评估后再排）
- [ ] OCR（ML Kit）与 tap_text 原语
- [ ] 定时任务（AlarmManager；熄屏拉起是否需要 Shizuku 再评估）
- [ ] 账号切换、运行时长限制、熄屏挂机等快捷选项
