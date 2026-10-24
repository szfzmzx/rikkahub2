#!/usr/bin/env bash
# 一键发版：升版本号 → 提交 → 打 tag → 推 master 和 tag
#
# 用法：
#   tools/cut-release.sh 3.1.3-me
#
# 推送 tag 后 GitHub Actions 的 Personal Release 会自动签名、校验并发布 Release，
# 手机上打开抽屉即可收到更新。
set -euo pipefail

VERSION="${1:-}"
if [ -z "$VERSION" ]; then
  echo "用法: tools/cut-release.sh <versionName>   例如: tools/cut-release.sh 3.1.3-me" >&2
  exit 1
fi

cd "$(dirname "$0")/.."

if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]]; then
  echo "版本号格式不对：$VERSION（要像 3.1.3-me 这样）" >&2
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
git push origin master
git push origin "$VERSION"
echo "已推送 tag $VERSION，去看 Actions 里的 Personal Release。"
