package pro.bixplayer.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import pro.bixplayer.player.util.AppVersion
import pro.bixplayer.player.util.DeviceIdentity

class AppVersionTest {

    @Test
    fun `detects an outdated app`() {
        assertThat(AppVersion.isOutdated("1.0.0", "1.1.0")).isTrue()
        assertThat(AppVersion.isOutdated("1.0", "1.0.1")).isTrue()
        assertThat(AppVersion.isOutdated("0.9.9", "1.0.0")).isTrue()
    }

    @Test
    fun `accepts an up to date app`() {
        assertThat(AppVersion.isOutdated("1.1.0", "1.1.0")).isFalse()
        assertThat(AppVersion.isOutdated("2.0.0", "1.9.9")).isFalse()
        assertThat(AppVersion.isOutdated("1.0.1", "1.0")).isFalse()
    }

    @Test
    fun `a broken minimum version never bricks the app`() {
        assertThat(AppVersion.isOutdated("1.0.0", "")).isFalse()
        assertThat(AppVersion.isOutdated("1.0.0", "abc")).isFalse()
        assertThat(AppVersion.isOutdated("", "1.0.0")).isFalse()
    }
}

class DeviceIdentityTest {

    @Test
    fun `hashes the android id deterministically`() {
        val a = DeviceIdentity.sha256("9774d56d682e549c")
        val b = DeviceIdentity.sha256("9774d56d682e549c")
        assertThat(a).isEqualTo(b)
        assertThat(a).hasLength(64)
        assertThat(a).matches("[0-9a-f]{64}")
        // the raw identifier must not be recoverable from the hash
        assertThat(a).doesNotContain("9774d56d")
    }

    @Test
    fun `different ids hash differently`() {
        assertThat(DeviceIdentity.sha256("a")).isNotEqualTo(DeviceIdentity.sha256("b"))
    }
}
