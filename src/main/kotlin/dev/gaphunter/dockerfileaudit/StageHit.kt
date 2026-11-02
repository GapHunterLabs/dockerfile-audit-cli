package dev.gaphunter.dockerfileaudit

/** One named build stage (`FROM ... AS name`) never referenced by a later `COPY --from=name` and not the file's final stage. [lineNumber] is 1-based. */
data class StageHit(val stageName: String, val lineNumber: Int)
