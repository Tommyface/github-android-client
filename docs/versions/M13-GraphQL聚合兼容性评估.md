# M13 · GraphQL 详情聚合兼容性评估（v0.7.0 · P2）

> 日期：2026-11-24
> 结论：**no-go，本轮后置**。理由为凭证类型在复用层不可达 + 真实 token 行为无法在本环境实测 + 收益/成本比低。
> 本文所有结论均附实测证据与代码定位，遵循「证据驱动、不臆造」纪律。

---

## 一、P13 对 M13 的原始约束

P13 评审原文（第四节 M13）：

> 仓库详情用一次 GraphQL 查询聚合 stars/forks/latestRelease/README，替代多次 REST round-trip。
> **注意**：fine-grained PAT **不支持** GraphQL API，仅 OAuth token / classic PAT 可用——实施前需按登录凭证类型降级（OAuth 走 GraphQL，PAT 走 REST），或直接后置。

即 M13 有两个硬前提：
1. 必须能按凭证类型（OAuth vs fine-grained PAT）降级；
2. 实施前必须验证凭证兼容性。

---

## 二、实测证据（本环境）

### 2.1 GraphQL 端点可达性与鉴权分支

| 请求 | HTTP | 响应 |
|---|---|---|
| `POST /graphql`（无 token） | 403 | `API rate limit exceeded for <出口IP>` |
| `POST /graphql`（`Bearer gho_*` 伪 token） | 401 | `{"message":"Bad credentials"}` |
| `POST /graphql`（`Bearer github_pat_*` 伪 token） | 401 | `{"message":"Bad credentials"}` |
| `POST /graphql`（`Bearer ghp_*` 伪 token） | 401 | `{"message":"Bad credentials"}` |

结论：端点可达，鉴权分支正常（无效 token → 401 Bad credentials，与 REST 的 403 限流可区分）。
**但本环境出口 IP 已被 REST 匿名限流、且无有效 token，无法实测「fine-grained PAT 调 GraphQL 是否被拒」这一决定是否可行的最关键行为。**

### 2.2 凭证类型在复用层的可达性（代码定位）

| 事实 | 代码位置 |
|---|---|
| `AuthSession.method: AuthMethod?`（OAUTH/PAT）存在，UI 层可识别凭证类型 | `feature/auth/domain/.../model/AuthModels.kt:20-23,42` |
| `AuthTokenProvider` 只暴露 `tokenFor(channel): String?`，**不暴露 method** | `core/domain/.../network/ApiModels.kt` |
| `repo:data` 依赖仅为 core.domain/core.data/repo.domain，**不依赖 auth.domain**，无法读取 `AuthMethod` | `feature/repo/data/build.gradle.kts` |
| `GitHubApiClient` 仅暴露 `request/requestConditional`，无 GraphQL（POST /graphql + JSON body）通道 | `core/data/.../network/GitHubApiClient.kt:38,51,142` |

结论：要在仓库层按凭证类型降级，必须二选一：
- 让 `core.domain.AuthTokenProvider` 增加凭证类型查询（污染 core 抽象，且 core 不应感知 auth 语义）；或
- 让 `repo:data` 反向依赖 `auth.domain`（破坏现有依赖方向，Konsist 架构门禁可能拦截）。

两者都不是低成本增量。

---

## 三、收益/成本分析

**收益**：GraphQL 聚合可把「详情 stars/forks + README + 最新 release」三次 REST 合并为一次 POST。但本项目现状：
- 详情 stars/forks/issues 已由 `GET /repos/{o}/{r}` **一次**返回（M4 之前即如此）；
- README 由 M6 独立端点加载；
- 最新 release 在 discover 模块，与仓库详情不在同一路径。

实际可省的仅 1–2 次 round-trip，且 P1/P2 已用 ETag 条件请求降低开销。

**成本**：新增 POST /graphql 通道 + 凭证类型贯穿 + fine-grained PAT 降级分支 + 测试替身改造 + 架构依赖调整。属于中高成本、跨模块改造。

**风险**：fine-grained PAT 用户占相当比例（P11 权限体检即围绕 PAT 设计），若无真实 token 实测就上线 GraphQL 主路径，可能在 PAT 用户上返回 403/「GraphQL not supported」，属线上事故风险。

---

## 四、决策与后续

**本轮决策**：M13 后置，产品代码不做改动，避免引入未经实测的凭证感知机制。

**重启前置条件**（满足全部再评估）：
1. 有一枚有效的 OAuth token（`gho_`/Device Flow 获取）与一枚 fine-grained PAT，可实测两者对 `/graphql` 的真实行为；
2. 明确凭证类型贯穿方案（建议 `AuthTokenProvider` 增加 `credentialKind()` 返回 core 层定义的枚举，不依赖 auth.domain）；
3. 确认 GraphQL 聚合字段与现有 UI 数据需求的交集足够大，收益覆盖改造成本。

**已完成的相邻增量**（P2 内）：M12 断点续传修复、M10 星标趋势、M11 Issue/PR 只读浏览，均不依赖 GraphQL。

---

## 五、负面清单复核

M13 后置与 P13「负面清单」一致：不引入不可控依赖、不突破「发现-评估-下载」工具型定位。GraphQL 聚合属优化项而非功能缺口，后置不影响 M6/M10/M11 已交付的用户价值。
