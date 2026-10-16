package com.oyeai.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckerTest {

    private val releases = """
        [
          {"tag_name":"v0.2.0","draft":false,"assets":[{"name":"app-release.apk","browser_download_url":"https://x/v020.apk"}]},
          {"tag_name":"build-7","draft":false,"assets":[{"name":"app-release.apk","browser_download_url":"https://x/b7.apk"}]},
          {"tag_name":"build-9","draft":true,"assets":[{"name":"app-release.apk","browser_download_url":"https://x/b9.apk"}]},
          {"tag_name":"build-8","draft":false,"assets":[{"name":"other.zip","browser_download_url":"https://x/b8.zip"}]},
          {"tag_name":"build-10","draft":false,"assets":[{"name":"app-release.apk","browser_download_url":"https://x/b10.apk"}]}
        ]
    """.trimIndent()

    @Test
    fun tagsMapToVersionCodes() {
        assertEquals(1007, UpdateChecker.versionCodeForTag("build-7"))
        assertNull(UpdateChecker.versionCodeForTag("v0.2.0"))
        assertNull(UpdateChecker.versionCodeForTag("build-x"))
    }

    @Test
    fun picksNewestBuildWithApk() {
        val update = UpdateChecker.pickNewest(releases, installedCode = 1000)
        assertEquals("build-10", update?.tag)
        assertEquals("https://x/b10.apk", update?.apkUrl)
    }

    @Test
    fun noUpdateWhenInstalledIsNewest() {
        assertNull(UpdateChecker.pickNewest(releases, installedCode = 1010))
    }

    @Test
    fun ignoresDraftsAndReleasesWithoutApk() {
        val onlyBadReleases = """
            [
              {"tag_name":"build-9","draft":true,"assets":[{"name":"app-release.apk","browser_download_url":"https://x/b9.apk"}]},
              {"tag_name":"build-8","draft":false,"assets":[{"name":"other.zip","browser_download_url":"https://x/b8.zip"}]}
            ]
        """.trimIndent()
        assertNull(UpdateChecker.pickNewest(onlyBadReleases, installedCode = 1000))
    }
}
