# P-300 · 界面文案字符串化与债务棘轮

- 来源: 本仓库自检（M0-18 收尾轮），观察日期 2026-11-27
- 落位: `app/src/main/res/values/strings.xml`、`core/presentation/src/main/res/values/strings.xml`、
  `konsist-test/.../ArchitectureTest.kt`（规则 13）、`tools/ui_audit.py` + `tools/audit_baseline.json`
- 声明: 本规格不引入新行为，只把**已存在的界面文案**从 Kotlin 字面量搬到资源，并给这件事装上可执行的尺子

## 外部可观察行为

1. 界面上的中文文案**全部**来自字符串资源；源码里不再出现中文字面量（注释除外）。
2. 带变量的文案用位置参数（`%1$s`）表达，不用字符串拼接，避免拼接出的文案无法本地化。
3. 设计系统（Frost）的文案归属 `core/presentation` 模块自己的资源，**不**依赖 app 模块——
   feature 模块复用设计系统时直接取用，不产生对 app 的反向依赖。
4. 文案缺失时**编译期**报错（`R.string.x` 不存在即编译失败），而不是运行时显示空白。

## 门禁行为（本轮的关键：尺子必须准）

| # | Given | When | Then |
|---|---|---|---|
| 1 | 新增或改名的界面文件里写了中文文案 | 运行 konsist 规则 13 | **失败**，并指出具体文件 |
| 2 | 文件里只有 KDoc / 行注释中的中文引号 | 运行 konsist 规则 13 | **通过**（注释不是文案） |
| 3 | 白名单里的文件已经清干净 | 运行 konsist 规则 13 | **失败**，提示删掉登记行（登记项失效也失败） |
| 4 | feature 层的界面文件写了中文文案 | 运行 konsist 规则 13 | **失败**（feature 层在扫描范围内） |
| 5 | 任意状态 | 运行 `bash tools/ui_audit.sh --strict` | 任一棘轮指标超标即**非零退出** |

## 度量口径（与门禁一致）

| 指标 | 方向 | R7 基线 | 说明 |
|---|---|---|---|
| 硬编码中文 UI 文件数 | max（只减不增） | 39 | 与规则 13 白名单同口径 |
| 业务屏裸色值文件数 | max（只减不增） | 3 | 内容调色板刻意例外（代码高亮 / Diff 语义色 / 标签数据色） |
| core/presentation 复用处数 | min（只升不降） | 6 | 目标 ≥ 10（随界面迁移） |
| 含 contentDescription 文件数 | min | 2 | 目标 ≥ 3 |
| 使用 semantics 文件数 | min | 1 | 列表 / 进度 / 选中态语义化 |

状态覆盖类指标（空 / 错误 / 重试 / 加载 / 离线 / 权限）是**下限**而非棘轮：
关键词计数本身脆弱，改个文案就误报，因此只提示、不拦人。

## 已清账（R7）

| 文件 | 模块 | 清掉的文案 |
|---|---|---|
| `DangerConfirmSheet.kt` | core:presentation | 确认 / 取消 / 输入确认提示 / 可见性与归档后果说明 |
| `FrostChrome.kt` | core:presentation | 六个图标的无障碍朗读文本 |
| `AppScaffold.kt` | app | 四个 Tab 的标题与副标题、设置标题、离线提示条 |
| `SettingsScreen.kt` | app | 分区标题、主题三态、版本页脚 |
| `DeepLinks.kt` | app | 入队成功 / 失败 / 无浏览器可打开的提示 |

## 后续

- 剩余 39 个文件为**已登记存量**，逐屏清账（每清一屏，从 `LEGACY_HARDCODED_STRING_FILES` 删一行）。
- 目标：硬编码中文 UI 文件数 → 0，同时规则 13 的白名单清空。
