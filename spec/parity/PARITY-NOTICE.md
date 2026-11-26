# PARITY-NOTICE · 对标与合规声明

> 适用范围：`Tommyface/github-android-client`（下称 **A**）在功能对标 `Buwrt/githup`（下称 **B**）时的
> 合规边界与执行纪律。本文件是《GHHub 最优修改报告》§9 清洁室 SOP 的仓库内落点。

## 一、基线

| 项 | 值 |
|---|---|
| 对标仓库 | `Buwrt/githup` |
| 锁定 commit | `45a7681` |
| 观察日期 | 2026-11-26 |
| 对标版本 | v1.2.20 |
| 基线文件 | `spec/parity/PARITY-BASELINE.md` |

**不追清单**：基线之后 B 新增的功能一律不进对标矩阵，只在基线文件登记一行「B@后续新增：不追」。
v1.0 验收以基线矩阵为准。基线每 90 天允许手动升一次（升级 = 新 PR 评审增量，不自动跟随）。

## 二、合规事实

1. **B 仓库无 LICENSE**，默认「保留全部权利」。因此 A 对 B 采取**清洁室复刻**：只复刻外部可观察行为，
   **零代码 / 零注释 / 零文案 / 零图标引入**，不 clone 进仓库、不 subtree vendor、不改名提交、不抄注释散文。
2. 唯一例外：取得 B 作者**书面授权**后，走 SOP S7 走受限引用路径，代码隔离到 `:vendor-githup-inspired`，
   并在 `THIRD-PARTY.md` 登记。
3. **GPL 禁区**：FastHub（GPL-3.0）、OpenHub（GPL-3.0）、diohub（GPL-3.0）零代码引用——
   引一行即导致 A 整体 GPL 化。
4. **可引码的合法第二参照**：`slapperwan/gh4a`（OctoDroid，Apache-2.0，活跃维护）。
   走**引码通道**而非清洁室：读码/移植均可，但须逐文件保留版权声明并在 `THIRD-PARTY.md` 登记。

## 三、七步法（S0–S7）

| 步 | 动作 | 产物 |
|---|---|---|
| S0 | 合规前置：记录基线 SHA / 获取时间 / 访问方式 | 本文件 + `PARITY-BASELINE.md` |
| S1 | 行为规格化：只写外部可观察行为 + Given/When/Then（≥3 条含 1 边界） | `spec/parity/P-XXX-*.md` |
| S2 | 独立设计：模块归属 / 数据流 / 状态机 / 错误模型 / 并发与离线策略 | 规格的「落位」段 |
| S3 | 独立实现：纯逻辑写成无 Android 依赖的纯 Kotlin | 对应模块源码 |
| S4 | 对照双跑：同输入喂本实现与行为描述，比较输出 | `P-XXX-comparison.md` |
| S5 | 门禁全绿 + 安全评审 | CI 记录 |
| S6 | 合并留痕：PR 描述含 Closes 规格 + 对照来源 + 独立实现声明 | PR 记录 |
| S7 | 授权后补录（如有）：NOTICE / THIRD-PARTY 登记 | `THIRD-PARTY.md` |

## 四、编号与分支对应

- 规格编号 `P-XXX` 同时是：**分支名**（`feature/P-XXX-slug`）、**提交前缀**（`feat(P-XXX): ...`）、**PR 标题 ID**。
- 一条 ID 一条分支，杜绝大杂烩 PR。由 konsist 规则 12 守护命名规范。

## 五、空壳模块处置（M0-14）

约定：**模块一旦纳入 `settings.gradle.kts` 的 feature 清单，必须有真实源码。**

- 已移除（零源码，仅有 build.gradle.kts）：`feature/release/{domain,data,presentation}`、`feature/share/presentation`。
  这两个域在 D1 阶段实现时**重新创建并直接填充**，不再预置空壳。
- `core/presentation` 保留为**设计系统与通用组件的归属地**（不得从业务域反向依赖它），
  其内容由 UI 里程碑交付；在内容落地前不得被当作通用工具箱使用。
