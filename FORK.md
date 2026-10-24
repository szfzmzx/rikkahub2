# RikkaHub Me —— 个人共存版

基于 [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub)（AGPL-3.0）的个人分支。
独立的 `applicationId`，可以和官方版装在同一台手机上，互不覆盖、数据互不干扰。

- 上游基线：`ad2c443`（v3.1.1）
- 本版包名：`com.rikkahub.me`（debug 变体为 `com.rikkahub.me.debug`）
- 本版桌面名：`RikkaHub Me`

## 与官方版的差异

| 项目 | 官方版 | 本版 |
| --- | --- | --- |
| applicationId | `me.rerere.rikkahub` | `com.rikkahub.me` |
| 桌面名 | RikkaHub | RikkaHub Me |
| Firebase 统计 / 崩溃上报 | 有 | **已移除**（构建不再需要 `google-services.json`） |
| 应用内更新检查 | 指向 `updates.rikka-ai.com` | **关闭**（`BuildConfig.IS_FORK`） |
| 签名 | 官方 keystore | 你自己的 keystore；debug 包用 CI 的 debug key |
| `namespace` / Kotlin 包名 | `me.rerere.rikkahub` | **保持不变**（有意为之） |
| 深链 / Intent action | `rikkahub://`、`me.rerere.rikkahub.action.*` | 保持不变（与官方版二维码互通） |

### 为什么只改 `applicationId`

Android 的“包名”由 `applicationId` 决定，共存只需要它不同。`namespace` 与 Kotlin 包名一旦改动，会牵动
551 个文件的 `package` / `import`、`R` 与 `BuildConfig` 的引用、`AndroidManifest` 里的类名、`shortcuts.xml`、
`compose_compiler_config.conf` 等，既容易回归，也让以后 `git merge upstream/master` 变得痛苦。
因此本分支刻意把改动面压到最小：**只改对外的身份，不改内部命名空间**。

## 分支与提交约定

- `master`：唯一主线，直接在这里提交个人改动。
- `upstream` remote 指向官方仓库，用于同步。
- 建议个人化改动一项一个 commit，信息前缀用 `feat(me):`，方便以后挑选或回滚。

## 首次推送到你自己的 GitHub

本仓库已带完整历史，但 `origin` 还是空的：

```bash
git remote add origin git@github.com:<你的账号>/<你的仓库>.git
git push -u origin master
```

推送后 `.github/workflows/personal-build.yml` 会自动开始构建。

### 提交身份

本仓库现有的 5 个 commit 是用占位身份 `RikkaHub Me <me@rikkahub.me>` 提交的（本机没有配置 git 身份）。
推之前建议改成你自己，否则 GitHub 上不会关联到你的账号：

```bash
git config user.name "你的名字"
git config user.email "你的邮箱"
git rebase --exec 'git commit --amend --no-edit --reset-author' upstream/master
```

### 关于 spotless

根 `build.gradle.kts` 里 `spotless { ratchetFrom("origin/master") }` 需要存在 `origin/master` 这个 ref。
推送到你自己的仓库并 `git fetch origin` 之后就有了；在还没有 origin 的本地环境跑 `spotlessCheck` 会报找不到 revision。

## 出包

### CI（推荐）

推到 `master`（或手动触发 `Personal Build`）后：

1. Actions → `Personal Build` → 最近一次运行 → 页面底部 **Artifacts**
2. `rikkahub-me-debug` 里有三个 APK（`universal` / `arm64-v8a` / `x86_64`），**装 `universal` 那个**
3. 流水线里带身份断言：APK 的 `applicationId` 和桌面名不符合预期会直接失败，不会产出错误产物

CI 会自己准备 JDK 21、Android SDK `platforms;android-37.2`、`build-tools;37.0.0`、Node 22 + pnpm 11，
并拉取 `material3/material-color-utilities` 子模块。

### 正式签名的 release 包

debug 包可以直接装，但签名是 CI 的 debug key。想要“正经”的 `com.rikkahub.me`（无 `.debug` 后缀），
先生成一个自己的 keystore 并备份好：

```bash
keytool -genkeypair -v \
  -keystore rikkahub-me.jks \
  -alias rikkahub-me \
  -keyalg RSA -keysize 4096 -validity 10000
```

再在仓库 Settings → Secrets and variables → Actions 里加四个 secret：

| Secret | 内容 |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 rikkahub-me.jks` 的输出 |
| `KEYSTORE_PASSWORD` | keystore 口令 |
| `KEY_ALIAS` | `rikkahub-me` |
| `KEY_PASSWORD` | key 口令 |

配好后再跑一次流水线，会额外产出 `rikkahub-me-release`。

> ⚠️ keystore 和口令丢了就再也无法覆盖升级已安装的 release 包，只能卸载重装。
> 另外 debug 包与 release 包签名不同，二者不能互相覆盖，切换时需要先卸载。

### 本地构建

需要：JDK 21、Android SDK（`platforms;android-37.2` + `build-tools;37.0.0`）、Node 22 + pnpm 11、
已初始化的子模块（`git submodule update --init --recursive`）。然后：

```bash
cd web-ui && pnpm install --frozen-lockfile && cd ..
./gradlew assembleGithubDebug          # 免签名
./gradlew assembleGithubRelease        # 需要在 local.properties 里配好签名
```

`local.properties` 的签名格式（相对 `app/` 模块目录）：

```properties
storeFile=app.key
storePassword=***
keyAlias=rikkahub-me
keyPassword=***
```

## 同步上游

```bash
git fetch upstream
git merge upstream/master
```

冲突热点只有这几处（因为改动集中）：

- `app/build.gradle.kts`（applicationId、`IS_FORK`、Firebase 依赖）
- `app/src/main/java/me/rerere/rikkahub/di/AppModule.kt`、`di/ViewModelModule.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatVM.kt`、`ChatDrawer.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingPreferencesNotificationPage.kt`
- `app/src/main/res/values*/strings.xml` 的 `app_name`
- `.github/workflows/`

上游以后若新增更新入口，记得在新位置补 `!BuildConfig.IS_FORK`，否则共存版又会提示安装官方包。

## 数据迁移

- 官方版和本版是两套独立沙箱，装好后都是空的。
- 迁移走应用内置的备份：官方版「设置 → 备份」导出，本版导入即可（只换了 `applicationId`，数据库结构一致）。
- 例外：SSH 密码 / 私钥这类用 Android Keystore 加密的内容绑定了应用，导入后解不开，需要重新填
  （上游 `SecretCipher` 已有“解密失败提示重填”的处理）。

## 已知差异与限制

- **debug 后缀**：CI 默认产物的包名是 `com.rikkahub.me.debug`，和正式包可以同时存在，但两者内容不共享。
- **深链冲突**：两版都注册 `rikkahub://`，扫供应商二维码导入时会弹系统选择器；保留 scheme 是为了和官方版互通二维码。
- **Web 服务器端口**：默认 8080，两版同时开启会抢占端口，在设置里改一个即可。
- **mDNS 名称**：默认 `rikkahub`，两版同时开启局域网服务时 jmDNS 会自动加后缀，不影响使用。
- **baseline profile**：`app/src/main/generated/baselineProfiles/startup-prof.txt` 是上游生成的产物，里面还留着
  Firebase 的类名。ART 会忽略找不到的条目，功能无影响；真要重生成需要真机跑 `:app:baselineprofile`。
- **崩溃排查**：移除 Crashlytics 后，未捕获异常只走 `CrashHandler` + logcat，用 `adb logcat` 看。

## 许可证

上游是 **AGPL-3.0**，本仓库保留原始 `LICENSE`。自己装机使用不受额外约束；一旦把 APK 分发给别人，
需要同时提供完整对应源码（AGPL 第 13 条对网络服务同样适用），并且不要以官方名义发布。

## 下一步个人化清单（尚未做）

- [ ] 启动器图标 / 启动图（`ic_launcher_background` 目前是接近纯色的 `#26262A`，换成 `@color/...` 即可，无需改 PNG）
- [ ] 主题色 / 排版偏好
- [ ] 关于页署名、版本号后缀（`versionName` 目前跟上游一致）
- [ ] 默认模型、默认助手、默认提示词
- [ ] 深链 scheme 与 Intent action 改成自己的（会失去与官方版二维码互通）
- [ ] 接自己的更新源（`UpdateChecker` 的 `API_URL` 与 `IS_FORK` 判断）
- [ ] 移除 `play` flavor 与 `IS_PLAY_STORE` 相关分支
