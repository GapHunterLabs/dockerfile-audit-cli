package dev.gaphunter.dockerfileaudit

/**
 * Plain-text line scanner for a multi-stage Dockerfile's named build
 * stages (`FROM <image> AS <name>`) -- flags a stage that is never
 * referenced by a later `COPY --from=<name>` (or `COPY --from=<name>`
 * inside a later `RUN --mount=...,from=<name>`) and isn't the file's
 * own final stage (the last `FROM` in the file, which is always the
 * real build target for a plain `docker build` with no `--target`).
 * An orphaned intermediate stage is real, wasted build time and a
 * confusing read for the next person touching the file.
 *
 * Manual, deliberate port of the scanner inside Gap Hunter Labs'
 * dockerfile-unused-stage-companion IntelliJ plugin -- identical
 * detection logic, kept as a standalone copy (mirror any rule change in
 * both).
 *
 * **v0.1 scope, stated honestly:** a stage referenced only via an
 * explicit `docker build --target=<name>` (not visible in the
 * Dockerfile text itself) will be flagged as a false positive -- this
 * tool can't see build invocations outside the file. Stage names
 * are matched case-insensitively (Docker itself is case-insensitive
 * here).
 */
object DockerfileStageScanner {

    private val FROM_WITH_STAGE = Regex(
        """^FROM\s+\S+\s+AS\s+([A-Za-z0-9_.-]+)\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val FROM_LINE = Regex("""^FROM\s+""", RegexOption.IGNORE_CASE)
    private val COPY_FROM_REF = Regex("""--from=([A-Za-z0-9_.-]+)""", RegexOption.IGNORE_CASE)

    fun scan(text: String): List<StageHit> {
        val lines = text.removePrefix("﻿").lines()
        val stages = mutableListOf<Pair<String, Int>>() // name to line number (1-based)
        val referenced = mutableSetOf<String>()
        var lastFromLineIndex = -1

        lines.forEachIndexed { index, rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachIndexed

            if (FROM_LINE.containsMatchIn(trimmed)) {
                lastFromLineIndex = index
                val stageMatch = FROM_WITH_STAGE.find(trimmed)
                if (stageMatch != null) {
                    stages += stageMatch.groupValues[1].lowercase() to (index + 1)
                }
            }

            for (refMatch in COPY_FROM_REF.findAll(trimmed)) {
                referenced += refMatch.groupValues[1].lowercase()
            }
        }

        if (stages.isEmpty() || lastFromLineIndex < 0) return emptyList()
        val finalStageLine = lastFromLineIndex + 1

        return stages
            .filter { (_, lineNumber) -> lineNumber != finalStageLine }
            .filter { (name, _) -> name !in referenced }
            .map { (name, lineNumber) -> StageHit(name, lineNumber) }
    }
}
