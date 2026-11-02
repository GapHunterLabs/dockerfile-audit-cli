package dev.gaphunter.dockerfileaudit

/**
 * Curated, deliberately non-exhaustive list of `RUN` patterns that
 * Docker's own official best-practices guidance (and the wider
 * community consensus reflected in hadolint, dive, and similar tools)
 * documents as commonly bloating a layer. Each check is a plain
 * substring/regex match on the instruction's argument text -- no
 * shell parsing, no attempt to actually understand the command, and
 * NEVER a size estimate (there is no way to know how many bytes a `RUN`
 * adds without executing it, which would mean a real build -- out of
 * scope for this static tool).
 *
 * Manual, deliberate port of the pattern set inside Gap Hunter Labs'
 * dockerfile-layer-size-companion IntelliJ plugin (mirror any rule
 * change in both).
 */
object ExpensiveRunPatterns {

    private val AGGREGATE_RM_APT_LISTS = Regex("""rm\s+-rf\s+/var/lib/apt/lists/\*""")

    /**
     * Checks a single `RUN` instruction against every known pattern and
     * returns every reason that matched (a single `RUN` can trip more
     * than one check at once, e.g. a Debian install missing both
     * `--no-install-recommends` and the apt-lists cleanup).
     */
    fun check(instruction: DockerfileInstruction): List<String> {
        val args = instruction.argsText
        val lower = args.lowercase()
        val reasons = mutableListOf<String>()

        if (mentionsAptGetInstall(lower) && "--no-install-recommends" !in lower) {
            reasons.add(
                "apt-get install without --no-install-recommends pulls in Recommends-tier " +
                    "packages by default, often adding tens of MB of docs/suggested tooling " +
                    "never used at runtime.",
            )
        }

        if ("apt-get update" in lower && !AGGREGATE_RM_APT_LISTS.containsMatchIn(lower)) {
            reasons.add(
                "apt-get update without rm -rf /var/lib/apt/lists/* in the SAME RUN leaves the " +
                    "package index cache baked permanently into this layer -- cleaning it in a " +
                    "later RUN doesn't shrink this layer, only a later one.",
            )
        }

        if (mentionsPipInstall(lower) && "--no-cache-dir" !in lower) {
            reasons.add(
                "pip install without --no-cache-dir leaves pip's download cache in the layer " +
                    "(often tens of MB for a non-trivial requirements.txt).",
            )
        }

        if ("npm install" in lower && "--production" !in lower && "npm ci" !in lower) {
            reasons.add(
                "npm install (vs. npm ci) resolves and can update the lockfile, and pulls " +
                    "devDependencies by default in a runtime image unless --production/--omit=dev is set.",
            )
        }

        if (mentionsCurlOrWgetInvocation(lower) && !mentionsCleanupOfDownload(lower)) {
            reasons.add(
                "downloads a file via curl/wget with no visible cleanup (rm) of the downloaded " +
                    "archive/installer in the same RUN -- the raw download stays in this layer " +
                    "even if a later RUN deletes it.",
            )
        }

        return reasons
    }

    /**
     * Detects the "several separate RUN instructions that could be
     * combined into one" pattern across a whole instruction list. Each
     * Docker layer has real per-layer storage/metadata overhead, so N
     * consecutive `RUN` shell commands that don't depend on an
     * intervening instruction are cheaper as one combined `RUN a && b`.
     */
    fun findCombinableRuns(instructions: List<DockerfileInstruction>): List<List<DockerfileInstruction>> {
        val groups = mutableListOf<List<DockerfileInstruction>>()
        var current = mutableListOf<DockerfileInstruction>()

        for (instruction in instructions) {
            if (instruction.keyword == "RUN") {
                current.add(instruction)
            } else {
                if (current.size >= 2) groups.add(current)
                current = mutableListOf()
            }
        }
        if (current.size >= 2) groups.add(current)
        return groups
    }

    private fun mentionsAptGetInstall(lower: String): Boolean =
        "apt-get install" in lower || "apt install" in lower

    private fun mentionsPipInstall(lower: String): Boolean =
        Regex("""pip3?\s+install""").containsMatchIn(lower)

    private val PACKAGE_INSTALL_CLAUSE = Regex("""\b(apt-get|apt|apk|yum|dnf)\s+(install|add)\b""")
    private val CURL_OR_WGET_WORD = Regex("""\b(curl|wget)\b""")

    /**
     * `curl`/`wget` appearing anywhere in the command isn't enough --
     * `apt-get install -y curl` (or `apk add wget`) installs the TOOL,
     * it never downloads anything during the build. Only a mention
     * outside a package-manager install clause counts as a real
     * invocation. Splits on shell chaining (`&&`, `;`, `|`) so a RUN
     * that both installs curl AND separately invokes it is still
     * flagged for the invocation half.
     */
    private fun mentionsCurlOrWgetInvocation(lower: String): Boolean =
        lower.split('&', ';', '|').any { clause ->
            CURL_OR_WGET_WORD.containsMatchIn(clause) && !PACKAGE_INSTALL_CLAUSE.containsMatchIn(clause)
        }

    private fun mentionsCleanupOfDownload(lower: String): Boolean =
        lower.contains("&& rm ") || lower.contains("&&rm ") || lower.trimEnd().endsWith("&& rm")
}
