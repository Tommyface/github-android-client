# ABCD · 后续迭代排期方案

> 日期：2026-11-25
> 输入：v0.12.0 收官后「列出其他未完成任务」的 ABCD 四类盘点
> 纪律沿用：**前置实测 → 复盘清单确认范围 → 编号实施 → 8 项门禁全绿 → bump 版本并全仓同步 → MD5/字节数入档 → 推送与 Release**。
> 定位不变：「发现 → 评估 → 下载」工具型客户端，零后端、可离线、零不可控依赖（承 P13 负面清单）。

---

## 一、ABCD 分类到版本批次的映射

| 类别 | 含义 | 排期策略 |
|---|---|---|
| **A** | 只读 REST 可做、低风险、可插入排期的长尾 | **本轮起分批实施**（真实迭代对象） |
| **B** | 写操作，与定位冲突 | **维持后置**，不进本轮；仅记录，不编码 |
| **C** | 受 API / 凭证限制 | **维持 no-go / 降级**，不臆造数据；仅热力图解前置时才重启评估 |
| **D** | 架构约束（通知 token、GraphQL 双栈） | **选择性评估**，随 A 类接触时顺带处理可达项 |

> 结论：**本轮迭代 = A 类分批落地 + D 类可达项顺带**；B/C 类保持现状，不因「列出来」而扩大定位。

---

## 二、A 类分批排期（只读 REST 长尾）

### v0.13.0（A 批一 · P0/P1 仓库只读增强）— versionCode 15 → 16

| 编号 | 条目 | 来源 | 复用能力 | 风险 |
|---|---|---|---|---|
| A1 | **分支切换**：详情页点击分支 → 作为 ref 驱动「浏览文件 / 下载 ZIP」 | 功能清单 P0「分支切换」 | `listBranches` 契约/API/UseCase 已就绪，`FileBrowser` 路由已支持 ref | 低（补 UI 选中态 + 传参） |
| A2 | **CI 检查状态 / Checks**：详情页只读展示最近一次 check runs / statuses | 功能清单 P1 | `GitHubApiClient` 统一网络层 + Repository/DI 模式 | 中（新端点 + DTO） |

### v0.14.0（A 批二 · P1 协作只读）— versionCode 16 → 17

| 编号 | 条目 | 来源 | 复用能力 | 风险 |
|---|---|---|---|---|
| A3 | **Actions Run 只读**：Run 列表 / 状态 / 日志入口 | 功能清单 P1 | 统一网络层 | 中（日志下载端点） |
| A4 | **代码行 Blame**：文件浏览页查看某文件 blame | 功能清单 P1 | FileBrowser + N8 高亮 | 中 |

### v0.15.0（A 批三 · P1 用户与组织扩展）— versionCode 17 → 18

| 编号 | 条目 | 来源 | 复用能力 | 风险 |
|---|---|---|---|---|
| A5 | **组织信息 / 成员** | 功能清单 P1 | Profile 模块模式 | 低 |
| A6 | **Gist 浏览**：个人页 Gist 数 → 可点进列表 | 功能清单 P1 / v0.10 遗留 | events/RepoList 模式 | 低 |
| A7 | **仓库 Settings 只读视图**（描述/默认分支/归档状态） | 功能清单 P1 | Repository 模型字段已含 | 低 |

> v0.16.0+（P2 长尾打包，视反馈启动）：Reactions / Insights / Security 告警 / Wiki / Codespaces / Pages 设置 / Artifact 下载 / 递归文件树 / 全文搜索。

---

## 三、B / C / D 类处理决定（不编码，仅记录）

### B 类 · 写操作（维持后置）
创建/编辑/删除文件、文件上传、Issue 写、PR 创建/合并、Code Review、Actions 触发/启停/重跑、分支保护、Collaborator/Secrets/Webhook 管理。
**理由**：与「发现-评估-下载」定位冲突（P13 已明确）。除非用户拍板扩大定位对标 CarGuo，否则不实现。

### C 类 · 受 API / 凭证限制（维持 no-go / 降级）
| 项 | 状态 | 解封条件 |
|---|---|---|
| 贡献热力图（真·日历） | 已降级跳网页 | 重启 M13 前置：GraphQL 通道 + classic/OAuth token 分流 |
| 官方 Trending / stars today | 已用 Search 近似替代 | 无官方 API，永久近似 |
| 仓库级总下载数 | 不展示 | 无公开 API |
| Projects v2 / Discussions | no-go | 仅 GraphQL + M13 no-go |
| 多账号 / Following 完整 feed / 账户级通知偏好 / 冲突在线解决 / 可视化 YAML | 跳网页或无 API | 无公开 API |

### D 类 · 架构约束（选择性评估）
- **通知 API token 限制**：`/notifications` 仅 classic PAT；界面已做，fine-grained token 用户受限。若 A 类触及通知再评估兼容方案。
- **GraphQL 双栈**：M13 no-go；仅当热力图解前置时重启。

---

## 四、每批纪律（不可省略）

1. **前置实测**：新端点先认证通道实测字段/限流，写入批次报告。
2. **编号实施**：domain → data → presentation → 导航接线，逐层可测。
3. **8 门禁全绿**：assemble / detekt / spotless / test / kover / konsist / lint / migration。
4. **版本 bump + 全仓同步**：`AndroidApplicationConventionPlugin` 版本号 + 所有硬编码引用。
5. **签名一致**：`59c63608…cdb8`，可覆盖安装。
6. **MD5/字节数入档** → 构建产物落地 → 推送 + Release（需 token 环境）。

---

## 五、本轮开工

默认从 **v0.13.0（A1 分支切换 + A2 CI/Checks）** 开工。如无异议按上述纪律推进；A 批先做前置实测与门禁，再 bump 版本与制品。
