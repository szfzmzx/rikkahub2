package me.rerere.rikkahub.utils

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.common.http.await
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale

private const val API_URL = "https://updates.rikka-ai.com/"

// 个人版直接读自己仓库的 GitHub Releases：tag 就是 versionName，例如 3.1.2-me
private const val FORK_RELEASES_URL = "https://api.github.com/repos/szfzmzx/rikkahub2/releases/latest"

// 发版时附带的小清单，用来拿 versionCode：版本号可能改命名方案（例如 3.1.6-me -> 3.1.1.7），
// 只比字符串会把新版本判成旧版本，versionCode 才是单调递增的。
private const val RELEASE_MANIFEST_ASSET = "update.json"

class UpdateChecker(
    private val client: OkHttpClient,
    appScope: AppScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val updateState: StateFlow<UiState<UpdateInfo>> = checkUpdate().stateIn(
        scope = appScope,
        started = SharingStarted.Lazily,
        initialValue = UiState.Loading,
    )

    private fun checkUpdate(): Flow<UiState<UpdateInfo>> = flow {
        emit(UiState.Loading)
        emit(
            UiState.Success(
                data = if (BuildConfig.IS_FORK) fetchForkRelease() else fetchOfficialRelease()
            )
        )
    }.catch {
        emit(UiState.Error(it))
    }.flowOn(Dispatchers.IO)

    private suspend fun fetchOfficialRelease(): UpdateInfo {
        val response = client.newCall(
            Request.Builder()
                .url(API_URL)
                .get()
                .addHeader(
                    "User-Agent",
                    "RikkaHub ${BuildConfig.VERSION_NAME} #${BuildConfig.VERSION_CODE}"
                )
                .build()
        ).await()
        if (!response.isSuccessful) {
            throw Exception("Failed to fetch update info")
        }
        return json.decodeFromString<UpdateInfo>(response.body.string())
    }

    /**
     * 个人版更新源：自己仓库的 GitHub Releases。
     *
     * 还没发布过 release（404）、被限流或断网时都返回“无更新”，避免抽屉里反复弹“检查失败”。
     * tag 直接当作版本号，所以发布时要让 tag 与 app 的 versionName 保持一致。
     */
    private suspend fun fetchForkRelease(): UpdateInfo {
        val noUpdate = UpdateInfo(
            version = BuildConfig.VERSION_NAME,
            publishedAt = java.time.Instant.now().toString(),
            changelog = "",
            downloads = emptyList(),
        )
        return try {
            val response = client.newCall(
                Request.Builder()
                    .url(FORK_RELEASES_URL)
                    .get()
                    .addHeader(
                        "User-Agent",
                        "RikkaHubMe ${BuildConfig.VERSION_NAME} #${BuildConfig.VERSION_CODE}"
                    )
                    .addHeader("Accept", "application/vnd.github+json")
                    .build()
            ).await()
            if (response.code == 404) return noUpdate
            if (!response.isSuccessful) throw Exception("GitHub Releases HTTP ${response.code}")
            val release = json.decodeFromString<GitHubRelease>(response.body.string())
            val info = gitHubReleaseToUpdateInfo(release, java.time.Instant.now().toString())
            val manifestUrl = release.assets
                .firstOrNull { it.name == RELEASE_MANIFEST_ASSET }
                ?.browserDownloadUrl
            // 版本号一样就不用再拉清单了，省一次请求
            val code = if (info.version == BuildConfig.VERSION_NAME) {
                null
            } else {
                manifestUrl?.let { fetchReleaseVersionCode(it) }
            }
            if (code != null && code > 0) info.copy(versionCode = code) else info
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            noUpdate
        }
    }

    /** 读发布清单里的 versionCode；拿不到就返回 null，调用方退回按版本号字符串比较 */
    private suspend fun fetchReleaseVersionCode(url: String): Int? = try {
        val response = client.newCall(
            Request.Builder()
                .url(url)
                .get()
                .addHeader(
                    "User-Agent",
                    "RikkaHubMe ${BuildConfig.VERSION_NAME} #${BuildConfig.VERSION_CODE}"
                )
                .build()
        ).await()
        if (!response.isSuccessful) {
            null
        } else {
            json.decodeFromString<ReleaseManifest>(response.body.string()).versionCode
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    fun downloadUpdate(context: Context, download: UpdateDownload) {
        runCatching {
            val request = DownloadManager.Request(download.url.toUri()).apply {
                // 设置下载时通知栏的标题和描述
                setTitle(download.name)
                setDescription("正在下载更新包...")
                // 下载完成后通知栏可见
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                // 允许在移动网络和WiFi下下载
                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                // 设置文件保存路径
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, download.name)
                // 允许下载的文件类型
                setMimeType("application/vnd.android.package-archive")
            }
            // 获取系统的DownloadManager
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            // 你可以保存返回的downloadId到本地，以便后续查询下载进度或状态
        }.onFailure {
            Toast.makeText(context, "Failed to update", Toast.LENGTH_SHORT).show()
            context.openUrl(download.url) // 跳转到下载页面
        }
    }
}

@Serializable
data class UpdateDownload(
    val name: String,
    val url: String,
    val size: String
)

@Serializable
data class UpdateInfo(
    val version: String,
    val publishedAt: String,
    val changelog: String,
    val downloads: List<UpdateDownload>,
    /** 发布清单里的 versionCode；老版本 Release 没有这个资产时为 null */
    val versionCode: Int? = null,
)

@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("body") val body: String = "",
    @SerialName("published_at") val publishedAt: String = "",
    @SerialName("assets") val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
internal data class ReleaseManifest(
    @SerialName("versionCode") val versionCode: Int = 0,
)

@Serializable
internal data class GitHubAsset(
    @SerialName("name") val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    @SerialName("size") val size: Long = 0L,
)

/**
 * 判断有没有新版本。
 *
 * 优先比 versionCode：版本号命名方案换过（`3.1.6-me` → `3.1.1.7`），按字符串比会把新版本判成旧版本。
 * Release 没带 update.json（老版本）时退回按版本号字符串比较。
 */
internal fun hasUpdate(
    info: UpdateInfo,
    localVersionName: String,
    localVersionCode: String,
): Boolean {
    val remoteCode = info.versionCode
    val code = localVersionCode.toIntOrNull()
    return if (remoteCode != null && remoteCode > 0 && code != null) {
        remoteCode > code
    } else {
        Version(info.version) > Version(localVersionName)
    }
}

/**
 * 把 GitHub Release 映射成应用内更新用的 [UpdateInfo]。
 *
 * tag 直接当版本号（发布时保证 tag == versionName），只保留 `.apk` 资产；
 * 正文为空时给一句兜底说明，避免更新卡片里的变更说明一片空白。
 */
internal fun gitHubReleaseToUpdateInfo(release: GitHubRelease, fallbackNow: String): UpdateInfo =
    UpdateInfo(
        version = release.tagName.trim().removePrefix("v"),
        publishedAt = release.publishedAt.ifBlank { fallbackNow },
        changelog = release.body.ifBlank { "小仆 ${release.tagName}" },
        downloads = release.assets
            .filter { it.name.endsWith(".apk", ignoreCase = true) }
            .map {
                UpdateDownload(
                    name = it.name,
                    url = it.browserDownloadUrl,
                    size = formatSize(it.size),
                )
            },
    )

private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    }
}

/**
 * 版本号值类，封装版本号字符串并提供比较功能
 *
 * 支持完整的 SemVer 规范：MAJOR.MINOR.PATCH[-prerelease][+build]
 * - 预发布版本优先级低于正式版：1.0.0-alpha < 1.0.0
 * - 预发布标识符按段逐个比较：数字按数值比较，字符串按字典序比较
 * - 预发布标识符优先级：alpha < beta < rc（通过字典序自然满足）
 * - build metadata（+号后面的部分）不影响优先级比较
 */
@JvmInline
value class Version(val value: String) : Comparable<Version> {

    private fun parse(): ParsedVersion {
        // 去掉 build metadata（+号后面的部分）
        val withoutBuild = value.split("+").first()
        // 分离主版本号和预发布标识符
        val hyphenIndex = withoutBuild.indexOf('-')
        val (coreStr, prereleaseStr) = if (hyphenIndex >= 0) {
            withoutBuild.substring(0, hyphenIndex) to withoutBuild.substring(hyphenIndex + 1)
        } else {
            withoutBuild to null
        }
        val core = coreStr.split(".").map { it.toIntOrNull() ?: 0 }
        val prerelease = prereleaseStr?.split(".")
        return ParsedVersion(core, prerelease)
    }

    override fun compareTo(other: Version): Int {
        val a = this.parse()
        val b = other.parse()

        // 先比较主版本号
        val maxLen = maxOf(a.core.size, b.core.size)
        for (i in 0 until maxLen) {
            val ap = if (i < a.core.size) a.core[i] else 0
            val bp = if (i < b.core.size) b.core[i] else 0
            if (ap != bp) return ap.compareTo(bp)
        }

        // 主版本号相同时比较预发布标识符
        // 有预发布标识符的版本优先级低于没有的：1.0.0-alpha < 1.0.0
        return when {
            a.prerelease == null && b.prerelease == null -> 0
            a.prerelease != null && b.prerelease == null -> -1
            a.prerelease == null && b.prerelease != null -> 1
            else -> comparePrerelease(a.prerelease!!, b.prerelease!!)
        }
    }

    companion object {
        fun compare(version1: String, version2: String): Int {
            return Version(version1).compareTo(Version(version2))
        }

        private fun comparePrerelease(a: List<String>, b: List<String>): Int {
            val maxLen = maxOf(a.size, b.size)
            for (i in 0 until maxLen) {
                // 字段少的优先级更低：1.0.0-alpha < 1.0.0-alpha.1
                if (i >= a.size) return -1
                if (i >= b.size) return 1

                val aNum = a[i].toIntOrNull()
                val bNum = b[i].toIntOrNull()

                val cmp = when {
                    // 都是字：按数值比较
                    aNum != null && bNum != null -> aNum.compareTo(bNum)
                    // 数字优先级低于字符串
                    aNum != null -> -1
                    bNum != null -> 1
                    // 都是字符串：按字典序比较
                    else -> a[i].compareTo(b[i])
                }
                if (cmp != 0) return cmp
            }
            return 0
        }
    }
}

private data class ParsedVersion(
    val core: List<Int>,
    val prerelease: List<String>?,
)

// 扩展操作符函数，使比较更直观
operator fun String.compareTo(other: Version): Int = Version(this).compareTo(other)
operator fun Version.compareTo(other: String): Int = this.compareTo(Version(other))
