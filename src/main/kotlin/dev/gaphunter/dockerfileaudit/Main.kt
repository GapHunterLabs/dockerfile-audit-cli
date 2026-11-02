package dev.gaphunter.dockerfileaudit

import java.io.File
import kotlin.system.exitProcess

private const val VERSION = "0.1.0"
private const val TOOL = "dockerfile-audit-cli"

private data class Args(
    val path: File,
    val format: String,
    val outFile: File?,
    val failOnFind: Boolean,
)

fun main(rawArgs: Array<String>) {
    val args = parseArgs(rawArgs) ?: return

    if (!args.path.exists()) {
        System.err.println("$TOOL: path not found: ${args.path}")
        exitProcess(2)
    }

    val hits = Scanner.scan(args.path)
    val output = when (args.format) {
        "sarif" -> renderSarif(hits)
        else -> renderText(hits, args.path)
    }

    if (args.outFile != null) {
        args.outFile.writeText(output)
    } else {
        println(output)
    }

    if (hits.isNotEmpty() && args.failOnFind) {
        exitProcess(1)
    }
}

private fun parseArgs(rawArgs: Array<String>): Args? {
    var path = File(".")
    var format = "text"
    var outFile: File? = null
    var failOnFind = true

    var i = 0
    while (i < rawArgs.size) {
        when (val arg = rawArgs[i]) {
            "--format" -> {
                format = rawArgs.getOrNull(++i) ?: usageError("--format needs a value (text|sarif)")
                if (format != "text" && format != "sarif") usageError("unknown --format '$format' (expected text|sarif)")
            }
            "--out" -> outFile = File(rawArgs.getOrNull(++i) ?: usageError("--out needs a file path"))
            "--no-fail" -> failOnFind = false
            "--version" -> { println("$TOOL $VERSION"); return null }
            "-h", "--help" -> { printHelp(); return null }
            else -> {
                if (arg.startsWith("-")) usageError("unknown flag '$arg'")
                path = File(arg)
            }
        }
        i++
    }
    return Args(path, format, outFile, failOnFind)
}

private fun usageError(message: String): Nothing {
    System.err.println("$TOOL: $message")
    printHelp()
    exitProcess(2)
}

private fun printHelp() {
    println(
        """
        $TOOL $VERSION -- local, offline Dockerfile security linting
        (ported from Gap Hunter Labs' dockerfile-missing-user-companion detection engine)

        Scans files recognised as Dockerfiles by name (Dockerfile, *.dockerfile,
        Dockerfile.<tag>) and flags every final build stage that runs as root --
        no non-root USER instruction in the stage that `docker build` actually runs.

        Usage: $TOOL [path] [options]

          path              File or directory to scan (default: current directory)
          --format <fmt>    Output format: text (default) or sarif
          --out <file>      Write output to a file instead of stdout
          --no-fail         Always exit 0, even if findings are reported
          --version         Print version and exit
          -h, --help        Print this help and exit

        Exit codes: 0 = no findings (or --no-fail), 1 = findings reported, 2 = usage/path error.
        """.trimIndent()
    )
}

// internal, not private: OutputFormatTest exercises these directly
// rather than only through the process-exiting main() entrypoint.
internal fun renderText(hits: List<ScanHit>, root: File): String {
    if (hits.isEmpty()) return "$TOOL: no findings in ${root.path}"
    val lines = hits.sortedWith(compareBy({ it.file }, { it.line })).map {
        "${it.file}:${it.line}:${it.column}: [${it.kind}] ${it.description}"
    }
    return (lines + "" + "$TOOL: ${hits.size} finding(s) in ${root.path}").joinToString("\n")
}

private val RULE_DESCRIPTIONS = mapOf(
    Scanner.MISSING_USER to "Final build stage runs as root (no non-root USER instruction)",
)

internal fun renderSarif(hits: List<ScanHit>): String {
    val rules = hits.map { it.kind }.distinct().sorted()
    val rulesJson = rules.joinToString(",\n") { kind ->
        """    {"id": "${jsonEscape(kind)}", "shortDescription": {"text": "${jsonEscape(RULE_DESCRIPTIONS[kind] ?: kind)}"}}"""
    }
    val resultsJson = hits.joinToString(",\n") { hit ->
        """    {
      "ruleId": "${jsonEscape(hit.kind)}",
      "level": "warning",
      "message": {"text": "${jsonEscape(hit.description)}"},
      "locations": [{
        "physicalLocation": {
          "artifactLocation": {"uri": "${jsonEscape(hit.file)}"},
          "region": {"startLine": ${hit.line}, "startColumn": ${hit.column}}
        }
      }]
    }"""
    }
    return """
{
  "${'$'}schema": "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
  "version": "2.1.0",
  "runs": [{
    "tool": {
      "driver": {
        "name": "$TOOL",
        "informationUri": "https://github.com/GapHunterLabs/dockerfile-audit-cli",
        "version": "$VERSION",
        "rules": [
$rulesJson
        ]
      }
    },
    "results": [
$resultsJson
    ]
  }]
}
    """.trimIndent()
}

private fun jsonEscape(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
