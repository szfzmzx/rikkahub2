# RikkaHub Me —— 个人共存版

基于 [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub)（AGPL-3.0）的个人分支。
独立的 `applicationId`，可以和官方版装在同一台手机上，互不覆盖、数据互不干扰。

- 上游基线：`ad2c443`（v3.1.1）
- 本版包名：`com.rikkahub.me`（debug 变体为 `com.rikkahub.me.debug`）
- 本版桌面名：`RikkaHub Me`
- 本版版本：`3.1.2-me`（versionCode 195）
- 仓库：<https://github.com/szfzmzx/rikkahub2>

## 与官方版的差异

| 项目 | 官方版 | 本版 |
| --- | --- | --- |
| applicationId | `me.rerere.rikkahub` | `com.rikkahub.me` |
| 桌面名 | RikkaHub | RikkaHub Me |
| 启动图标 | 近黑背景 `#26262A` | 深青背景 `#0E4A44`（只换背景一层，前景不变） |
| Firebase 统计 / 崩溃上报 | 有 | **已移除**（构建不再需要 `google-services.json`） |
| 应用内更新检查 | `updates.rikka-ai.com` | **本仓库的 GitHub Releases**（`releases/latest`，tag 即版本号） |
| 签名 | 官方 keystore | 自己的 keystore（仓库 secrets 已配置），debug 包仅用于验证 |
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

## 远程与提交身份

- `origin` = `https://github.com/szfzmzx/rikkahub2.git`（个人版仓库）
- `upstream` = `https://github.com/rikkahub/rikkahub.git`（官方仓库，用于同步）
- 提交身份已统一为 `szfzmzx <264301070+szfzmzx@users.noreply.github.com>`

### 关于 spotless

根 `build.gradle.kts` 里 `spotless { ratchetFrom("origin/master") }` 需要存在 `origin/master` 这个 ref。
本仓库里已经有对应的远端跟踪 ref；换一台机器 clone 后 `git fetch origin` 就有了，
否则本地跑 `spotlessCheck` 会报找不到 revision。

## 出包

### 日常验证（debug，CI）

推到 `master`（或手动触发 `Personal Build`）后，Actions → `Personal Build` → 最近一次运行 → 页面底部 **Artifacts**：

- `rikkahub-me-debug` 里有三个 APK（`universal` / `arm64-v8a` / `x86_64`），包名是 `com.rikkahub.me.debug`
- 流水线带身份断言：`applicationId` 和桌面名不符合预期会直接失败，不会产出错误产物
- ⚠️ debug 包的签名 key 由 GitHub runner 每次重新生成，**不同次构建的 debug 包不能互相覆盖升级**，只适合临时试装

CI 会自己准备 JDK 21、Android SDK `platforms;android-37.2`、`build-tools;37.0.0`、Node 22 + pnpm 11，
并拉取 `material3/material-color-utilities` 子模块。

### 发版（release，签名 + 自动发布）

正式包由 `.github/workflows/personal-release.yml` 产出，触发条件是**推一个和 `versionName` 完全相同的 tag**：

```bash
# 1. 改版本号（versionCode 与 versionName 一起加）
#    app/build.gradle.kts: versionCode = 196, versionName = "3.1.3-me"
git commit -am "chore(me): 版本号 3.1.3-me (196)"
git push origin master

# 2. 打同名 tag 并推送
git tag 3.1.3-me
git push origin 3.1.3-me
```

tag 推送后会依次：校验 tag == `versionName` → 用 secrets 里的 keystore 签 `assembleGithubRelease` →
aapt2 校验 `applicationId` / 桌面名 → 把 `rikkahub-me-<tag>.apk` 发到 GitHub Releases（自动生成变更说明）。

嫌手动改两处麻烦，可以用脚本一步到位（自动 `versionCode + 1`、提交、打 tag、推 master 和 tag）：

```bash
tools/cut-release.sh 3.1.3-me
```

发布完成后，手机上装的旧版本打开抽屉就能看到更新卡片（应用内更新读本仓库 `releases/latest`），
点一下走系统 DownloadManager 下载并安装。

> 版本比较按 SemVer：`3.1.2-me` < `3.1.3-me`，`3.1.2-me` < `3.1.2`。`-me` 后缀只是标记，不影响排序，
> 但 tag 里的版本号必须**单调递增**，否则更新卡片不会出现。

已发布：`3.1.2-me`（versionCode 195）——首个个人版 release，资产 `rikkahub-me-3.1.2-me.apk`（约 18 MB universal 包）。

### 回归测试

`.github/workflows/personal-test.yml` 在每次推 `master` 时跑 `./gradlew test`（全模块 JVM 单元测试），
和出包流水线分开看：**出包绿 ≠ 测试绿**。

个人版自己的用例在 `app/src/test/java/me/rerere/rikkahub/utils/UpdateCheckerForkTest.kt`，
锁定“GitHub Release JSON → `UpdateInfo`”的映射（apk 资产过滤、大小格式化、空正文/空时间兜底、`-me` 版本排序）。

### 签名密钥

keystore 已生成并写入仓库 secrets（`KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`），
本地备份在工作区的 `keystore/` 目录（`rikkahub-me.jks` + `password.txt`，权限 600，不在仓库里、不会被提交）。

> ⚠️ keystore 和口令丢了就再也无法覆盖升级已安装的 release 包，只能卸载重装。
> **请把 `keystore/` 目录另行备份到安全的地方。**
> 另外 debug 包与 release 包签名不同，二者不能互相覆盖，切换时需要先卸载。

当前签名证书（`CN=RikkaHub Me, OU=Personal, O=RikkaHub Me, C=CN`）的 SHA-256 指纹：

```
2bfabeff12dbd9f556a02aeee2671733f10fa616fc0c11abb8cd97c1ca337314
```

每次发版流水线的 “Verify signature” 步骤都会打印它，应该始终是这一个值。
想校验手动下载的 APK，不用装 Android SDK：

```bash
python3 tools/verify-apk-signature.py rikkahub-me-3.1.2-me.apk
```

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
- `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingPreferencesNotificationPage.kt`、`SettingAboutPage.kt`
- `app/src/main/java/me/rerere/rikkahub/utils/UpdateChecker.kt`（fork 更新源）
- `app/src/main/res/values*/strings.xml` 的 `app_name` 与 `about_page_*`
- `app/src/main/res/values/colors.xml`、`mipmap-anydpi-v26/ic_launcher.xml`
- `.github/workflows/`

上游若改版更新逻辑（`UpdateInfo` 字段、`Version` 比较规则），`fetchForkRelease()` 里把 GitHub Release 映射成
`UpdateInfo` 的那段要跟着改；tag 与 `versionName` 的约定也不能变，否则更新卡片会算错版本。

### 关于 `play` 渠道（有意保留）

上游的 `github` / `play` 两个 flavor 保持原样：个人版 CI 只构建 `github` 渠道，`play` 渠道不产出任何东西。
代码层面保留它，是为了让每次 `git merge upstream/master` 不用在 `build.gradle.kts` 的 flavor 块里反复解冲突；
`IS_PLAY_STORE` 的判断也照旧（Play 版不检查更新、不显示更新提醒）。删掉它省不了多少体积，只会增加同步成本。

## 数据迁移

- 官方版和本版是两套独立沙箱，装好后都是空的。
- 迁移走应用内置的备份：官方版「设置 → 备份」导出，本版导入即可（只换了 `applicationId`，数据库结构一致）。
- 例外：SSH 密码 / 私钥这类用 Android Keystore 加密的内容绑定了应用，导入后解不开，需要重新填
  （上游 `SecretCipher` 已有“解密失败提示重填”的处理）。

## 已知差异与限制

- **更新源**：应用内更新走 `api.github.com`，国内网络可能需要代理；请求失败或被限流时按“无更新”处理，不会弹错误卡片。
- **debug 后缀**：CI 日常产物的包名是 `com.rikkahub.me.debug`，和正式包可以同时存在，但两者数据不共享。
- **debug 包签名**：CI 每次用新生成的 debug key，多次构建的 debug 包互相装不上，长期使用请装 release 包。
- **深链冲突**：两版都注册 `rikkahub://`，扫供应商二维码导入时会弹系统选择器；保留 scheme 是为了和官方版互通二维码。
- **Web 服务器端口**：默认 8080，两版同时开启会抢占端口，在设置里改一个即可。
- **mDNS 名称**：默认 `rikkahub`，两版同时开启局域网服务时 jmDNS 会自动加后缀，不影响使用。
- **baseline profile**：`app/src/main/generated/baselineProfiles/startup-prof.txt` 是上游生成的产物，里面还留着
  Firebase 的类名。ART 会忽略找不到的条目，功能无影响；真要重生成需要真机跑 `:app:baselineprofile`。
- **崩溃排查**：移除 Crashlytics 后，未捕获异常只走 `CrashHandler` + logcat，用 `adb logcat` 看。

## 许可证

上游是 **AGPL-3.0**，本仓库保留原始 `LICENSE`。自己装机使用不受额外约束；一旦把 APK 分发给别人，
需要同时提供完整对应源码（AGPL 第 13 条对网络服务同样适用），并且不要以官方名义发布。

## 下一步个人化清单

- [x] 启动器图标换成个人配色（`@color/ic_launcher_background_me`）
- [x] 关于页标注“个人版”与包名
- [x] 版本号带 `-me` 后缀（当前 `3.1.2-me`）
- [x] 应用内更新接自己的 GitHub Releases
- [x] 把 `./gradlew test` 接进 CI（`Personal Test` 流水线）
- [ ] 主题色 / 排版偏好
- [ ] 默认模型、默认助手、默认提示词
- [ ] 深链 scheme 与 Intent action 改成自己的（会失去与官方版二维码互通）
- [~] 移除 `play` flavor —— 决定保留，理由见上文“关于 `play` 渠道”
