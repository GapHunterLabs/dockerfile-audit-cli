package dev.gaphunter.dockerfileaudit

/**
 * Plain-ASCII startup banner. Shown only when a human is likely watching a real terminal, never when the
 * output is meant to be parsed: a machine-readable format, a redirected `--out` file, or a non-interactive
 * process (a script, a CI runner, a pipe) all suppress it by default.
 *
 * The decision is a pure function of its inputs ([isConsole] comes from `System.console() != null` at the
 * call site) so it can be tested without a real terminal attached.
 */
internal object Banner {

    const val TEXT = """
  ___   _   ___   _  _ _   _ _  _ _____ ___ ___   _      _   ___ ___
 / __| /_\ | _ \ | || | | | | \| |_   _| __| _ \ | |    /_\ | _ ) __|
| (_ |/ _ \|  _/ | __ | |_| | .` | | | | _||   / | |__ / _ \| _ \__ \
 \___/_/ \_\_|   |_||_|\___/|_|\_| |_| |___|_|_\ |____/_/ \_\___/___/

dockerfile-audit-cli -- Dockerfile security & hygiene linting
github.com/GapHunterLabs/dockerfile-audit-cli"""

    /**
     * @param bannerFlag `--banner`: force it on, even piped or machine-readable.
     * @param noBannerFlag `--no-banner`: force it off, always wins over [bannerFlag].
     * @param isConsole whether a real interactive console is attached (`System.console() != null`).
     * @param machineReadable the run is `--format sarif` or writes to `--out`: a human isn't reading stdout.
     */
    internal fun shouldShow(bannerFlag: Boolean, noBannerFlag: Boolean, isConsole: Boolean, machineReadable: Boolean): Boolean {
        if (noBannerFlag) return false
        if (bannerFlag) return true
        return isConsole && !machineReadable
    }
}
