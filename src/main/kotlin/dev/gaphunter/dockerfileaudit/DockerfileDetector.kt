package dev.gaphunter.dockerfileaudit

/**
 * Decides whether a file is a Dockerfile, by name only. Manual,
 * deliberate port of the `DockerfileDetector` used by Gap Hunter Labs'
 * dockerfile-missing-user-companion IntelliJ plugin (conservative,
 * opt-in-by-name only). Kept as a standalone copy here, not a shared
 * module: this CLI builds as a plain JVM app while the plugin depends on
 * the IntelliJ Platform Gradle plugin -- if the detection rules change
 * in one place, mirror the change in the other.
 */
object DockerfileDetector {

    private val EXACT_NAMES = setOf("dockerfile")

    /**
     * A trailing extension here means the file is prose/documentation
     * (a cheatsheet, a notes file) that just happens to start with
     * "dockerfile." -- not a variant Dockerfile anyone actually builds
     * (real variants look like `Dockerfile.prod`/`Dockerfile.arm64`,
     * never `Dockerfile.md`). Confirmed against a real-world corpus:
     * ~10% of GitHub files matching `dockerfile.*` by name are `.md`
     * cheatsheets with unrelated, often non-buildable `FROM` lines used
     * as documentation examples.
     */
    private val DOCUMENTATION_SUFFIXES = setOf("md", "markdown", "mdx", "rst", "adoc")

    fun isDockerfile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        if (lower in EXACT_NAMES) return true
        if (lower.endsWith(".dockerfile")) return true
        if (lower.startsWith("dockerfile.")) {
            return lower.substringAfterLast('.') !in DOCUMENTATION_SUFFIXES
        }
        return false
    }
}
