# dockerfile-audit-cli

Local, offline Dockerfile security linting from the command line or a
GitHub Action — no IDE required. Flags every Dockerfile whose **final
build stage runs as root**: the stage a plain `docker build` actually
runs never drops to a non-root `USER`, so the container starts as root
by default — a real privilege-escalation / container-escape risk that
Docker's own `USER` guide calls out.

**Status: v0.1, early.** Built as a companion channel to Gap Hunter
Labs' [dockerfile-missing-user-companion](https://github.com/GapHunterLabs/dockerfile-missing-user-companion)
IntelliJ plugin — same detection rules
([`DockerfileUserScanner.kt`](src/main/kotlin/dev/gaphunter/dockerfileaudit/DockerfileUserScanner.kt)
is a manual, deliberate port, not a shared module — see the file header
for why), packaged for teams that don't use a JetBrains IDE, or want
the check in CI rather than (or in addition to) the editor.

One check ships in v0.1 (missing non-root `USER` in the final stage).
The CLI is structured to grow into a small suite of pure-text
Dockerfile checks as more of them are ported from the catalog; it is
not, and does not claim to be, a full Dockerfile linter like hadolint.

## Why a manual port instead of one shared module?

This project and the IntelliJ plugin build independently — one is a
plain JVM app, the other depends on the IntelliJ Platform Gradle
plugin. Sharing the scanner as a real Gradle module was possible but
not done for v0.1; keeping two copies in sync by hand is a known,
deliberate trade-off, not an oversight. If you change the detection
rules in one place, mirror the change in the other.

## Install / build

Requires JDK 21.

```bash
git clone https://github.com/GapHunterLabs/dockerfile-audit-cli.git
cd dockerfile-audit-cli
./gradlew shadowJar
java -jar build/libs/dockerfile-audit-cli-0.1.0.jar --help
```

## Usage

```bash
java -jar dockerfile-audit-cli-0.1.0.jar [path] [options]

  path              File or directory to scan (default: current directory)
  --format <fmt>    Output format: text (default) or sarif
  --out <file>      Write output to a file instead of stdout
  --no-fail         Always exit 0, even if findings are reported
  --version         Print version and exit
  -h, --help        Print this help and exit

Exit codes: 0 = no findings (or --no-fail), 1 = findings reported, 2 = usage/path error.
```

Files are recognised as Dockerfiles by name (`Dockerfile`,
`*.dockerfile`, `Dockerfile.<tag>`); `Dockerfile.md` and similar
documentation files are deliberately skipped.

Example:

```bash
$ java -jar dockerfile-audit-cli-0.1.0.jar ./my-repo
my-repo/services/api/Dockerfile:1:1: [DOCKERFILE_MISSING_USER] Final build stage has no non-root USER instruction -- the container runs as root by default

dockerfile-audit-cli: 1 finding(s) in ./my-repo
```

## GitHub Action

This repository is itself a composite GitHub Action. Point a workflow
at it directly:

```yaml
name: Dockerfile audit

on: [push, pull_request]

permissions:
  contents: read
  security-events: write   # only needed if upload-sarif stays 'true'

jobs:
  audit:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: GapHunterLabs/dockerfile-audit-cli@v0.1.0
        with:
          path: '.'
          fail-on-find: 'true'
          upload-sarif: 'true'
```

Inputs: `path` (default `.`), `fail-on-find` (default `true`),
`upload-sarif` (default `true`, uploads to GitHub Code Scanning via
`github/codeql-action/upload-sarif` — needs `security-events: write`
and Code Scanning available on the repo; public repos have this free,
private repos need GitHub Advanced Security). Outputs: `finding-count`,
`sarif-file`.

**Known limitation, honestly noted:** the action builds this project
from source on every run (see [`action.yml`](action.yml)) rather than
downloading a pre-built release jar — simple for v0.1, but it costs
every consumer a Gradle build. Publishing tagged release jars and
switching the action to download one is a real follow-up, not done
yet. The action itself has been reviewed against GitHub Actions'
documented composite-action behavior but has **not yet been run in a
live GitHub Actions workflow** — treat it as unverified in that
specific environment until it has.

## What this tool is not

It reasons over a Dockerfile's real multi-stage structure as plain
text, not a full Docker parser. Known blind spots, stated honestly:

- A `USER` instruction split across lines with a trailing `\` is not
  recognised (single-line match only) — rare, since the instruction is
  short, but a real gap.
- It never checks that the referenced user actually exists (`USER
  appuser` with no prior `RUN useradd appuser` is a different problem,
  out of scope here).
- It decides root-ness from the *last effective* `USER` in the final
  stage, following `FROM <earlier-stage>` chains inside the same file;
  it does not resolve `USER` baked into an external base image pulled
  from a registry (it can't see inside that image without a network
  call, which this tool never makes).

It's meant to catch the common, careless case cheaply, not to replace
a dedicated container-hardening product for anything security-critical.

## Privacy

See [PRIVACY.md](PRIVACY.md) — short version: this tool makes zero
network calls, reads only the files you point it at, and sends
nothing anywhere.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
