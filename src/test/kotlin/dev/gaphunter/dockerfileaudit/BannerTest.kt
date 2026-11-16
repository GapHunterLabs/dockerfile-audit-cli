package dev.gaphunter.dockerfileaudit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BannerTest {

    @Test
    fun `defaults to shown in an interactive text run`() {
        assertTrue(Banner.shouldShow(bannerFlag = false, noBannerFlag = false, isConsole = true, machineReadable = false))
    }

    @Test
    fun `defaults to hidden when not a real console -- piped or scripted`() {
        assertFalse(Banner.shouldShow(bannerFlag = false, noBannerFlag = false, isConsole = false, machineReadable = false))
    }

    @Test
    fun `defaults to hidden for sarif even in a real console`() {
        assertFalse(Banner.shouldShow(bannerFlag = false, noBannerFlag = false, isConsole = true, machineReadable = true))
    }

    @Test
    fun `defaults to hidden when writing to --out even in a real console`() {
        // machineReadable=true also covers --out: main() sets it when args.outFile != null.
        assertFalse(Banner.shouldShow(bannerFlag = false, noBannerFlag = false, isConsole = true, machineReadable = true))
    }

    @Test
    fun `--banner forces it on even when piped and machine-readable`() {
        assertTrue(Banner.shouldShow(bannerFlag = true, noBannerFlag = false, isConsole = false, machineReadable = true))
    }

    @Test
    fun `--no-banner forces it off even in an interactive text run`() {
        assertFalse(Banner.shouldShow(bannerFlag = false, noBannerFlag = true, isConsole = true, machineReadable = false))
    }

    @Test
    fun `--no-banner wins over --banner when both are somehow true`() {
        assertFalse(Banner.shouldShow(bannerFlag = true, noBannerFlag = true, isConsole = true, machineReadable = false))
    }

    @Test
    fun `the banner text itself stays plain ASCII, no trailing whitespace, fits 76 columns`() {
        val lines = Banner.TEXT.lines()
        for (line in lines) {
            if (!line.all { it.code in 0..127 }) fail("non-ASCII character in banner line: $line")
            if (line != line.trimEnd()) fail("trailing whitespace in banner line: '$line'")
            if (line.length > 76) fail("banner line is ${line.length} chars, over the 76-column budget: $line")
        }
    }

    @Test
    fun `the banner names the tool`() {
        assertTrue(Banner.TEXT.contains("dockerfile-audit-cli"))
    }
}
