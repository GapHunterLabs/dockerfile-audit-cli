package dev.gaphunter.dockerfileaudit

/** The final stage of a Dockerfile has no non-root `USER` instruction. [anchorLineNumber] (1-based) is the final stage's own `FROM` line -- there's no `USER` line to anchor on when one is missing. */
data class MissingUserHit(val anchorLineNumber: Int)
