# 本地真机端到端（Maestro）

> 定位：M4-10 的场景资产 + 本机执行手册。
> **CI 不跑设备测试**：GitHub hosted runner 无 `/dev/kvm`，emulator 37.x 拒绝软件 CPU 模拟
> （`nightly` 的「设备环节不可用声明」步骤每次都会写明这一点）。设备验证走本地真机。

## 一、前置

```bash
# 1. 设备可见（USB 调试已开，或无线调试）
adb devices

# 2. 安装 Maestro（一次性）
curl -Ls "https://get.maestro.mobile.dev" | bash
export PATH="$PATH:$HOME/.maestro/bin"

# 3. 装包（debug 或正式版均可，包名一致 com.gy.githubclient）
adb install -r app/build/outputs/apk/debug/app-debug.apk
# 正式产物：github-android-client-v0.19.1.apk
```

## 二、跑法

```bash
# 全套（除 02 外都需要已登录状态）
maestro test .maestro

# 只跑登录闭环：PAT 从环境变量注入，不进仓库
maestro test -e GITHUB_PAT=github_pat_xxx .maestro/02-sign-in-pat.yaml

# 一键包装脚本（装包 + 跑全量，自动带上 GITHUB_PAT）
tools/run_maestro_local.sh app/build/outputs/apk/debug/app-debug.apk
```

首次建议顺序：`01` → `02`（登录）→ 其余场景。

## 三、场景清单（12 条）

| 文件 | 覆盖 | 是否需登录 |
|---|---|---|
| `01-login-page` | Device Flow / PAT 主通道 / 通知通道绑定三入口齐备 | 否（`clearState`） |
| `02-sign-in-pat` | PAT 登录闭环 → 落到起始页 | 否（场景内登录） |
| `03-home-repo-list` | 起始页 `Route.Home` 与底部导航 | 是 |
| `04-bottom-nav` | 仓库/发现/搜索/我的 四 tab 往返 | 是 |
| `05-discover-categories` | 发现页分类 chips 切换 | 是 |
| `06-discover-rank-tabs` | 趋势/新发布/热门 三口径说明 | 是 |
| `07-profile-entries` | 我的页 12 个功能入口 + 退出登录 | 是 |
| `08-notifications` | 通知中心（未读计数用正则）+ 返回 | 是 |
| `09-starred` | 星标页排序 chips | 是 |
| `10-downloads-mirror` | 下载管理 → 镜像加速设置 | 是 |
| `11-library` | 应用库入口与批量更新 | 是 |
| `12-settings-theme` | 主题切换 + 说明文案 | 是 |

「登录后」类场景假设设备上已有有效凭据；`clearState: true` 会把登录态一起清掉，非登录场景不要加。

## 四、凭据约定

- PAT **只从 `-e GITHUB_PAT=` 注入**，任何场景文件里不得出现真实 token；
- 场景文件里出现 `${GITHUB_PAT}` 是变量引用，不是泄漏；
- 若需要在报告中留证据，只贴场景名与断言结果，不要贴 token。

## 五、维护约定

- 断言文案必须与 `strings.xml` / 界面实现**逐字一致**（本批全部取自代码实测，未使用推测文本）；
- 文案改动（P-209/P-210 这类字符串化重构）后必须同步更新对应场景，否则场景会假红；
- 带动态值的断言用正则（如未读计数 `通知中心（未读 [0-9]+）`）；
- Emoji 前缀（`⭐` / `🔔` / `🎨`）在部分键盘/字体环境可能匹配不稳，若本地首跑出现假红，
  优先把断言换成去 Emoji 的稳定片段，并在 PR 里注明改因。

## 六、首跑状态（必须如实标注）

本批场景**尚未在真机上执行过一次**——沙箱无设备、CI 无 KVM。
首次真机跑的失败项请回到本文件更新状态，并区分两类原因：

1. **场景写错**（选择器/文案不准）→ 改场景；
2. **应用真缺陷**→ 提 issue / 修代码。

不要为了让场景变绿而放宽断言。
