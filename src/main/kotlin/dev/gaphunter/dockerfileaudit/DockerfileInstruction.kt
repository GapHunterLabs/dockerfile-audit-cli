package dev.gaphunter.dockerfileaudit

/**
 * One parsed Dockerfile instruction. [lineNumber] is the 0-based number
 * of the FIRST line of the instruction (where a `\`-continued
 * instruction spans several source lines, it anchors to the first).
 */
data class DockerfileInstruction(
    val keyword: String,
    /** Raw argument text, continuation backslashes already joined and normalized to single spaces. */
    val argsText: String,
    val lineNumber: Int,
)

/**
 * Minimal line-by-line Dockerfile scanner -- a hand-rolled lexer for a
 * small, stable, line-oriented syntax: one instruction keyword per
 * (possibly `\`-continued) statement, no nesting, no braces.
 *
 * Manual, deliberate port of the parser inside Gap Hunter Labs'
 * dockerfile-layer-size-companion IntelliJ plugin, trimmed to what the
 * CLI's RUN-pattern checks need: the byte-size estimation (COPY/ADD arg
 * parsing, build-context sizing, `.dockerignore` matching, editor-only
 * inlay offsets) is deliberately left behind -- it's an editor estimate
 * that needs the real build context, not a CI finding. Kept as a
 * standalone copy (mirror any rule change in both).
 */
object DockerfileParser {

    private val KNOWN_KEYWORDS = setOf(
        "FROM", "RUN", "COPY", "ADD", "WORKDIR", "ENV", "ARG", "LABEL",
        "EXPOSE", "VOLUME", "USER", "ENTRYPOINT", "CMD", "SHELL",
        "STOPSIGNAL", "HEALTHCHECK", "ONBUILD", "MAINTAINER",
    )

    fun parse(text: String): List<DockerfileInstruction> {
        val lines = text.removePrefix("﻿").split("\n")
        val result = mutableListOf<DockerfileInstruction>()

        var i = 0
        while (i < lines.size) {
            val trimmed = lines[i].trim()

            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                i++
                continue
            }

            val firstSpace = trimmed.indexOfFirst { it.isWhitespace() }
            val keywordCandidate = (if (firstSpace == -1) trimmed else trimmed.substring(0, firstSpace)).uppercase()

            if (keywordCandidate !in KNOWN_KEYWORDS) {
                // Malformed / unrecognized line (stray text, a typo) -- never
                // throw, just skip it. Honest limitation, not a crash.
                i++
                continue
            }

            val instructionStartLine = i
            val argsBuilder = StringBuilder(if (firstSpace == -1) "" else trimmed.substring(firstSpace + 1))

            // Follow `\`-line continuations, joining them into one logical
            // instruction.
            var currentLine = trimmed
            while (currentLine.endsWith("\\") && i + 1 < lines.size) {
                if (argsBuilder.isNotEmpty() && argsBuilder.last() == '\\') {
                    argsBuilder.setLength(argsBuilder.length - 1)
                }
                i++
                currentLine = lines[i].trim()
                if (argsBuilder.isNotEmpty() && !argsBuilder.endsWith(" ")) argsBuilder.append(' ')
                argsBuilder.append(currentLine)
            }

            result.add(
                DockerfileInstruction(
                    keyword = keywordCandidate,
                    argsText = argsBuilder.toString().trim(),
                    lineNumber = instructionStartLine,
                ),
            )
            i++
        }

        return result
    }

    fun isRunKeyword(keyword: String): Boolean = keyword == "RUN"
}
