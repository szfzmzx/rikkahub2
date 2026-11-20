package me.rerere.rikkahub.utils

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 个人版的更新源是 GitHub Releases，这里锁定“Release JSON → UpdateInfo”的映射规则，
 * 免得以后改字段时更新卡片悄悄失效。
 */
class UpdateCheckerForkTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `maps release with apk asset`() {
        val payload = """
            {
              "tag_name": "3.1.1.7",
              "body": "修了几个 bug",
              "published_at": "2026-10-25T02:00:00Z",
              "assets": [
                {"name": "rikkahub-me-3.1.1.7.apk", "browser_download_url": "https://example.com/a.apk", "size": 62914560},
                {"name": "source.zip", "browser_download_url": "https://example.com/s.zip", "size": 1024}
              ]
            }
        """.trimIndent()

        val info = gitHubReleaseToUpdateInfo(
            release = json.decodeFromString<GitHubRelease>(payload),
            fallbackNow = "fallback",
        )

        assertEquals("3.1.1.7", info.version)
        assertEquals("修了几个 bug", info.changelog)
        assertEquals("2026-10-25T02:00:00Z", info.publishedAt)
        assertEquals(1, info.downloads.size)
        assertEquals("rikkahub-me-3.1.1.7.apk", info.downloads.single().name)
        assertEquals("https://example.com/a.apk", info.downloads.single().url)
        assertEquals("60.0 MB", info.downloads.single().size)
        // 清单由 fetchForkRelease 单独取，映射本身不带 versionCode
        assertNull(info.versionCode)
    }

    @Test
    fun `update manifest asset is not offered as download`() {
        val payload = """
            {
              "tag_name": "3.1.1.8",
              "assets": [
                {"name": "update.json", "browser_download_url": "https://example.com/update.json", "size": 64},
                {"name": "rikkahub-me-3.1.1.8.apk", "browser_download_url": "https://example.com/a.apk", "size": 2048}
              ]
            }
        """.trimIndent()

        val info = gitHubReleaseToUpdateInfo(
            release = json.decodeFromString<GitHubRelease>(payload),
            fallbackNow = "fallback",
        )

        assertEquals(1, info.downloads.size)
        assertEquals("rikkahub-me-3.1.1.8.apk", info.downloads.single().name)
    }

    @Test
    fun `parses version code from release manifest`() {
        val manifest = json.decodeFromString<ReleaseManifest>(
            """{"versionCode": 200, "versionName": "3.1.1.7"}"""
        )
        assertEquals(200, manifest.versionCode)
    }

    @Test
    fun `falls back when body and date are empty`() {
        val payload = """{"tag_name":"v3.1.1.9","assets":[]}"""

        val info = gitHubReleaseToUpdateInfo(
            release = json.decodeFromString<GitHubRelease>(payload),
            fallbackNow = "2026-10-25T00:00:00Z",
        )

        assertEquals("3.1.1.9", info.version)
        assertEquals("2026-10-25T00:00:00Z", info.publishedAt)
        assertEquals("小仆 v3.1.1.9", info.changelog)
        assertTrue(info.downloads.isEmpty())
    }

    @Test
    fun `version code wins over version name`() {
        // 换命名方案时的迁移：3.1.1.7 (200) 必须能覆盖已装的 3.1.6-me (199)，
        // 只按字符串比会把 3.1.1.7 判成旧版本。
        val info = UpdateInfo(
            version = "3.1.1.7",
            publishedAt = "2026-10-26T00:00:00Z",
            changelog = "",
            downloads = emptyList(),
            versionCode = 200,
        )
        assertTrue(hasUpdate(info, localVersionName = "3.1.6-me", localVersionCode = "199"))
        assertFalse(hasUpdate(info, localVersionName = "3.1.1.7", localVersionCode = "200"))
        assertFalse(hasUpdate(info, localVersionName = "3.1.1.8", localVersionCode = "201"))
    }

    @Test
    fun `falls back to version name without manifest`() {
        val info = UpdateInfo(
            version = "3.1.1.8",
            publishedAt = "2026-10-26T00:00:00Z",
            changelog = "",
            downloads = emptyList(),
            versionCode = null,
        )
        assertTrue(hasUpdate(info, localVersionName = "3.1.1.7", localVersionCode = "200"))
        assertFalse(hasUpdate(info, localVersionName = "3.1.1.8", localVersionCode = "201"))
        assertFalse(hasUpdate(info, localVersionName = "3.1.1.9", localVersionCode = "202"))
    }

    @Test
    fun `local revision versions order as expected`() {
        assertTrue(Version("3.1.1.8") > Version("3.1.1.7"))
        assertTrue(Version("3.1.1.10") > Version("3.1.1.9"))
        assertTrue(Version("3.1.2.1") > Version("3.1.1.99"))
        assertTrue(Version("3.1.1.1") > Version("3.1.1"))
        assertEquals(0, Version.compare("3.1.1.7", "3.1.1.7"))
    }

    @Test
    fun `legacy me suffix versions still parse`() {
        assertTrue(Version("3.1.3-me") > Version("3.1.2-me"))
        assertTrue(Version("3.1.2-me") > Version("3.1.1"))
        assertTrue(Version("3.1.2") > Version("3.1.2-me"))
        assertEquals(0, Version.compare("3.1.2-me", "3.1.2-me"))
    }
}
