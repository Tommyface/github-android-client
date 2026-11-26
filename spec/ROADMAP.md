# 执行路线图（依据《GHHub 最优修改报告》）

> 本文件是仓库内的执行主线，与 `docs/versions/`（历史交付记录）、`spec/parity/`（行为规格）配套。
> 状态标记：✅ 已完成 ｜ 🔄 进行中 ｜ ⏳ 未开始。

## 阶段 0 · 地基（3~4 天）— 让项目"可审计"

| 项 | 内容 | 状态 |
|---|---|---|
| M0-01 | 版本单一真源迁 `gradle.properties`，约定插件改读属性 | ✅ |
| M0-02 | CI 三处一致校验（`gradle.properties` == tag == `version.json`） | ✅ |
| M0-03 | `ci.yml`：主路径调 `verify.sh` + 并行预检 + 版本校验 job | ✅ |
| M0-04 | `verify.sh` CI 化：SDK 探测、fast 模式、版本与覆盖率校验 | ✅ |
| M0-05 | `release.yml`：签名构建 + 硬断言 + 产物三件套 + 三处一致校验 | ✅ |
| M0-06 | release 密钥从 Secrets 还原；无密钥直接失败（禁明文兜底） | ✅ |
| M0-07 | release 开启混淆 + 资源压缩 + ProGuard 规则 | ✅ |
| M0-08 | 签名指纹钉子改为钉 release 指纹 | ✅（新密钥已生成，Secrets 已托管，debug/release 按构建变体分流） |
| M0-09 | kover 阈值门禁（`tools/check_coverage.py`，阈值 60%） | ✅ |
| M0-10 | Room 结构安全门禁 + schema 导出 + DownloadDatabase v1→v2 迁移 | ✅（真实迁移测试待 schema 首次 bump） |
| M0-11 | konsist 2 → 12 条规则 | ✅ |
| M0-12 | 依赖治理：删 KSafe/jitpack、修正 alias、toolchain 统一 17 | ✅ |
| M0-13 | 清理脚手架残留（PlaceholderScreen / DownloadEntryScreen / 过期 TODO） | ✅ |
| M0-14 | 空壳模块定案：移除零源码模块，`core/presentation` 明确为设计系统归属地 | ✅ |
| M0-15 | 17 份交付报告归档 `docs/versions/` + 版本索引表 | ✅ |
| M0-16 | spec 骨架：PARITY-NOTICE / BASELINE / 模板 / P-101 / ADR-001 / 威胁模型 | ✅ |
| M0-17 | 分支保护配置说明 + PR 模板 + ISSUE 模板 + dependabot + gitleaks 配置 | ✅ |
| M0-18 | 字符串资源化（全量硬编码中文清零） | ⏳（依赖 UI 里程碑的组件化改造） |

## 阶段 1 · D0 发版与更新闭环（4~5 天）— 产出 v0.19.0

| 项 | 内容 | 状态 |
|---|---|---|
| M1-01 | P-101 UpdateLevel 纯函数 + 15 条验收用例 | ✅ |
| M1-02 | 指纹双信号（统一取 Release 附件 `*.sha256`，废弃 `target_commitish`） | ⏳ |
| M1-03 | version.json 双通道 + 跳过记指纹 + 30min 安装 TTL | ⏳ |
| M1-04 | 更新中心 UI（8 状态机可视化） | ⏳ |
| M1-05 | 镜像熔断 + 健康度（成功率÷延迟权重） | ⏳ |
| M1-06 | CHANGELOG / RELEASE_NOTES 自动生成 | ⏳ |
| M1-07 | 诊断与崩溃体系（分层 Tag + 落盘 + 导出脱敏） | ⏳ |
| M1-08 | 安装守卫移植（包名 / 签名 / versionCode / 未知来源权限） | ⏳ |
| M1-09 | Link header / Cursor 分页 | ⏳ |
| M1-10 | 网络韧性：指数退避+抖动、Retry-After、二级限流熔断 | ⏳ |
| M1-11 | `GitHubApiClient` 收敛点补测（分支覆盖 ≥80%） | ⏳ |

## 阶段 2 · D1 高价值写能力（约 1.5 周）

MutationExecutor / 审计日志 / 危险确认 / Issue-PR 写 / Release 创建 / 文件上传 / Actions 日志 /
仓库设置 / 凭据能力矩阵 / 通知中心 / Markwon 可选路径 / 应用库溯源 —— 详见报告 §4.3。

## 阶段 3 · D2 + UI 体系（约 3 周）

GraphQL 开关 / 热力图降级 / PR 合并 / **UI 体系落地（设计系统 + 18 组件）** /
26 屏规格与 Paparazzi / 无障碍与响应式 / 契约测试 / ViewModel 拆分 / ACRA —— 详见报告 §4.4。

## 阶段 4 · D3 长尾（约 2 周）

TOTP / 扫码 / 正文翻译（默认离线）/ 多账号 / 五环降级版 / 收敛内置浏览器 / Lists /
性能基线 / Maestro —— 详见报告 §4.5。

## 已知冲突与决策

`feat/frost-ui` 分支已实现 Frost 设计体系、MutationExecutor、审计库、权限能力矩阵、链接解析、
README 翻译与 CI workflow，与阶段 2/3 存在重叠。**合并策略见《GHHub 最优修改报告》§5.1 与 §14.2**：
先完成本分支的阶段 0（CI 与门禁是所有合并的前置），再按 G-对照逐屏验收 frost-ui 的 UI 体系，
通过后以「设计系统归属 core/presentation」的方式收编，避免两套设计体系并存。
