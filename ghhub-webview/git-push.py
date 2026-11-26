"""
通过 git 协议推送 GHHub 源码到目标仓库分支（比 REST 逐 blob 快且抗抖动）。

流程：clone(带重试) → checkout/创建分支 → 复制本地文件到 ghhub-webview/ 与仓库根 .github/ → commit → push。
幂等：分支已存在时基于其最新 commit 追加；不存在时从 main 创建。
PAT 仅需内存拼接认证 URL，不落盘到工作区。
"""
import os, sys, shutil, subprocess, tempfile, time

PAT = os.environ.get("GH_PAT", "").strip()
OWNER, REPO = "Tommyface", "github-android-client"
BRANCH = "feat/ghhub-webview-cover-install"
SRC = "/workspace/GHHub-Android"
AUTH_URL = "https://x-access-token:%s@github.com/%s/%s.git" % (PAT, OWNER, REPO)

def run(cmd, cwd=None, timeout=240):
    r = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, timeout=timeout)
    if r.returncode != 0:
        print("FAIL cmd=%s\nSTDERR:%s\nSTDOUT:%s" % (cmd[:3], r.stderr[-800:], r.stdout[-800:]), flush=True)
        raise RuntimeError("git failed: %s" % cmd[0])
    return r.stdout

def collect():
    files = []
    for root, dirs, names in os.walk(SRC):
        dirs[:] = [d for d in dirs if d not in (".git", "build", ".gradle")]
        for n in names:
            full = os.path.join(root, n)
            rel = os.path.relpath(full, SRC).replace(os.sep, "/")
            if rel.endswith("gradle-wrapper.jar") or rel.endswith(".local.jks"):
                continue
            if os.path.getsize(full) == 0:
                continue
            remote = rel if rel.startswith(".github/") else "ghhub-webview/" + rel
            files.append((remote, full))
    return sorted(files)

def main():
    if not PAT:
        print("ERROR: GH_PAT not set"); sys.exit(1)
    work = tempfile.mkdtemp(prefix="ghhub-push-")
    try:
        ok = False
        for i in range(6):
            try:
                print("clone attempt %d ..." % (i + 1), flush=True)
                run(["git", "clone", "--quiet", AUTH_URL, work], timeout=240)
                ok = True; break
            except Exception as e:
                print("clone retry %d: %s" % (i + 1, str(e)[:120]), flush=True)
                time.sleep(2 * (i + 1))
        if not ok:
            raise RuntimeError("git clone failed after retries")

        # 基于远端分支或 main
        chk = subprocess.run(["git", "rev-parse", "--verify", "origin/" + BRANCH],
                             cwd=work, capture_output=True, text=True)
        base = "origin/" + BRANCH if chk.returncode == 0 else "origin/main"
        print("base =", base, flush=True)
        run(["git", "checkout", "--quiet", "-B", BRANCH, base], cwd=work)

        files = collect()
        print("copying %d files ..." % len(files), flush=True)
        for remote, full in files:
            dst = os.path.join(work, remote)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copyfile(full, dst)
        gw = os.path.join(work, "ghhub-webview", "gradlew")
        if os.path.exists(gw):
            os.chmod(gw, 0o755)

        run(["git", "add", "-A"], cwd=work)
        diff = subprocess.run(["git", "diff", "--cached", "--quiet"], cwd=work)
        if diff.returncode == 0:
            print("NOTHING TO COMMIT (already up to date)", flush=True)
            return

        msg = ("feat(ghhub-webview): GHHub 更新助手 + 覆盖安装链路\n\n"
               "- WebView 壳 + 更新状态机 + 镜像选源 + 多应用管理\n"
               "- 覆盖安装：固定签名 + InstallGuard(签名/版本/权限) + CI 递增版本发布\n"
               "- 以独立子目录 ghhub-webview/ 交付，不改动既有 Compose 工程")
        run(["git", "-c", "user.name=ghhub-bot", "-c", "user.email=ghhub-bot@users.noreply.github.com",
             "commit", "-q", "-m", msg], cwd=work)

        pushed = False
        for i in range(6):
            try:
                print("push attempt %d ..." % (i + 1), flush=True)
                run(["git", "push", "--quiet", "origin", BRANCH], cwd=work, timeout=240)
                pushed = True; break
            except Exception as e:
                print("push retry %d: %s" % (i + 1, str(e)[:120]), flush=True)
                time.sleep(2 * (i + 1))
        if not pushed:
            raise RuntimeError("git push failed after retries")

        sha = run(["git", "rev-parse", "HEAD"], cwd=work).strip()
        print("DONE commit=%s branch=%s" % (sha[:12], BRANCH), flush=True)
    finally:
        shutil.rmtree(work, ignore_errors=True)

if __name__ == "__main__":
    main()
