"""
一键推送到 GitHub（幂等：分支已存在时基于其最新 commit 追加）。

用法：
    export GH_PAT='你的可写 fine-grained PAT'
    python3 push-to-github.py
    unset GH_PAT

PAT 所需权限（Settings → Developer settings → PAT → Fine-grained tokens）：
    - Resource owner: Tommyface
    - Repository access: 仅选 github-android-client
    - Repository permissions: Contents = Read and write（关键！只读会 403）
      + Workflows = Read and write（推送 .github/workflows/ 所需）

推送布局：本地源码 → 远端 ghhub-webview/ 子目录；CI workflow → 仓库根 .github/workflows/。
"""
import os, sys, json, base64, time, urllib.request, urllib.error

PAT = os.environ.get("GH_PAT", "").strip()
OWNER = "Tommyface"
REPO = "github-android-client"
BRANCH = "feat/ghhub-webview-cover-install"
SRC_ROOT = "/workspace/GHHub-Android"
API = "https://api.github.com"

def api(method, path, data=None, retries=10):
    body = json.dumps(data).encode() if data is not None else None
    headers = {
        "Authorization": "Bearer " + PAT,
        "Accept": "application/vnd.github+json",
        "User-Agent": "ghhub-push",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    if body is not None:
        headers["Content-Type"] = "application/json"
    last = None
    for i in range(retries):
        try:
            req = urllib.request.Request(API + path, data=body, headers=headers, method=method)
            with urllib.request.urlopen(req, timeout=40) as r:
                txt = r.read().decode()
                return json.loads(txt) if txt else {}
        except urllib.error.HTTPError as e:
            err = e.read().decode()
            # 429 / 5xx 属瞬时故障，退避重试；404 等交给上层处理
            if e.code in (429, 500, 502, 503, 504):
                last = e
                time.sleep(1.5 * (i + 1))
                continue
            raise RuntimeError("HTTP %d %s %s" % (e.code, path, err[:200]))
        except Exception as e:
            last = e
            time.sleep(1.5 * (i + 1))
    raise RuntimeError("network failed %s: %s" % (path, str(last)[:120]))

def ensure_branch():
    # 取 main 的 sha
    main = api("GET", "/repos/%s/%s/branches/main" % (OWNER, REPO))
    sha = main["commit"]["sha"]
    # 检查分支是否已存在
    try:
        br = api("GET", "/repos/%s/%s/branches/%s" % (OWNER, REPO, BRANCH))
        print("branch exists, sha=", br["commit"]["sha"][:10])
        return br["commit"]["sha"], True
    except RuntimeError as ex:
        # 仅 404 表示分支确实不存在；其它错误必须暴露，避免误判后再误建
        if "HTTP 404" not in str(ex):
            raise
    api("POST", "/repos/%s/%s/git/refs" % (OWNER, REPO), {"ref": "refs/heads/" + BRANCH, "sha": sha})
    print("branch created from main sha=", sha[:10])
    return sha, False

def collect_files():
    files = []
    for root, dirs, names in os.walk(SRC_ROOT):
        dirs[:] = [d for d in dirs if d not in (".git", "build", ".gradle")]
        for n in names:
            full = os.path.join(root, n)
            rel = os.path.relpath(full, SRC_ROOT).replace(os.sep, "/")
            # 排除二进制 wrapper jar（由 setup-gradle 提供，仓库内用 gradle 命令）
            if rel.endswith("gradle-wrapper.jar"):
                continue
            # 排除解码后的明文密钥（只推 base64 文本）
            if rel.endswith(".local.jks"):
                continue
            # 排除空目录占位
            if os.path.getsize(full) == 0:
                continue
            with open(full, "rb") as f:
                content = f.read()
            # workflow 放仓库根，其余放 ghhub-webview/ 子目录
            if rel.startswith(".github/"):
                remote_path = rel
            else:
                remote_path = "ghhub-webview/" + rel
            files.append((remote_path, content))
    return sorted(files)

def push(files, base_sha):
    print("pushing %d files..." % len(files))
    tree_entries = []
    for idx, (path, content) in enumerate(files, 1):
        blob = api("POST", "/repos/%s/%s/git/blobs" % (OWNER, REPO), {
            "content": base64.b64encode(content).decode(),
            "encoding": "base64",
        })
        mode = "100755" if path.endswith("/gradlew") else "100644"
        tree_entries.append({"path": path, "mode": mode, "type": "blob", "sha": blob["sha"]})
        if idx % 10 == 0 or idx == len(files):
            print("  blobs %d/%d" % (idx, len(files)))
    # 用 main 的 tree 作为 base，仅叠加我们的文件（子目录独立，不触碰原有文件）
    main_commit = api("GET", "/repos/%s/%s/git/commits/%s" % (OWNER, REPO, base_sha))
    base_tree = main_commit["tree"]["sha"]
    tree = api("POST", "/repos/%s/%s/git/trees" % (OWNER, REPO), {
        "base_tree": base_tree,
        "tree": tree_entries,
    })
    commit = api("POST", "/repos/%s/%s/git/commits" % (OWNER, REPO), {
        "message": "feat(ghhub-webview): GHHub 更新助手 + 覆盖安装链路\n\n- WebView 壳 + 更新状态机 + 镜像选源 + 多应用管理\n- 覆盖安装：固定签名 + InstallGuard(签名/版本/权限) + CI 递增版本发布\n- 以独立子目录 ghhub-webview/ 交付，不改动既有 Compose 工程",
        "tree": tree["sha"],
        "parents": [base_sha],
    })
    api("PATCH", "/repos/%s/%s/git/refs/heads/%s" % (OWNER, REPO, BRANCH), {"sha": commit["sha"]})
    print("COMMIT:", commit["sha"])
    return commit["sha"]

if __name__ == "__main__":
    if not PAT:
        print("ERROR: GH_PAT not set"); sys.exit(1)
    base_sha, exists = ensure_branch()
    files = collect_files()
    print("files:", len(files), "workflow:", [p for p,_ in files if p.startswith('.github/')])
    sha = push(files, base_sha)
    print("DONE commit=%s" % sha[:12])
