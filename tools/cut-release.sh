#!/usr/bin/env bash
# 一键发版：升版本号 → 提交 → 打 tag → 推 master 和 tag
#
# 用法：
#   tools/cut-release.sh            自动把当前版本号最后一段 +1（3.1.1.7 -> 3.1.1.8）
#   tools/cut-release.sh 3.1.2.1    上游升到 3.1.2 后，手动指定新的基线
#
# 版本号规则：<上游版本>.<本地修订号>，例如 3.1.1.7 表示基于上游 3.1.1 的第 7 次个人发布。
# 上游发新版时，基线跟着上游走、修订号从 1 重新开始（3.1.2.1）。
#
# 推送 tag 后 GitHub Actions 的 Personal Release 会自动签名、校验并发布 Release，
# 手机上打开抽屉即可收到更新。
set -euo pipefail

cd "$(dirname "$0")/.."

CURRENT=$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1)

VERSION="${1:-}"
if [ -z "$VERSION" ] || [ "$VERSION" = "--bump" ]; then
  VERSION=$(python3 - "$CURRENT" <<'PY'
import sys

cur = sys.argv[1]
parts = cur.split(".")
if len(parts) == 4 and all(p.isdigit() for p in parts):
    parts[-1] = str(int(parts[-1]) + 1)
    print(".".join(parts))
else:
    sys.exit(f"当前版本号 {cur} 不是 <上游>.<修订号> 格式，请手动指定，例如 tools/cut-release.sh 3.1.1.1")
PY
)
fi

if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "版本号格式不对：$VERSION（要像 3.1.1.7 这样：上游版本 + 本地修订号，不要 -me 后缀）" >&2
  exit 1
fi

if git rev-parse -q --verify "refs/tags/$VERSION" > /dev/null; then
  echo "tag $VERSION 已存在" >&2
  exit 1
fi

NEW_CODE=$(python3 - "$VERSION" <<'PY'
import re
import sys

path = "app/build.gradle.kts"
source = open(path, encoding="utf-8").read()
match = re.search(r"versionCode = (\d+)", source)
if not match:
    sys.exit("找不到 versionCode")
code = int(match.group(1)) + 1
source = re.sub(r"versionCode = \d+", f"versionCode = {code}", source, count=1)
source = re.sub(r'versionName = "[^"]*"', f'versionName = "{sys.argv[1]}"', source, count=1)
open(path, "w", encoding="utf-8").write(source)
print(code)
PY
)

echo "新版本：$VERSION (versionCode $NEW_CODE)"
git add app/build.gradle.kts
git commit -m "chore(me): 版本号 $VERSION ($NEW_CODE)"
git tag "$VERSION"
# 默认推 origin；需要临时换地址（例如带 token 的 https）时设 PUSH_REMOTE 环境变量
REMOTE="${PUSH_REMOTE:-origin}"
git push "$REMOTE" master
git push "$REMOTE" "$VERSION"
echo "已推送 tag $VERSION，去看 Actions 里的 Personal Release。"
