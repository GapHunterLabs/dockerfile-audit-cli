# dockerfile-audit-cli

Local, offline Dockerfile security & hygiene linting from the command
line or a GitHub Action: no IDE required. Four static checks:

- **`DOCKERFILE_MISSING_USER`**: the final build stage runs as root
  (no non-root `USER` in the stage a plain `docker build` actually
  runs): a real privilege-escalation / container-escape risk Docker's
  own `USER` guide calls out.
- **`DOCKERFILE_UNUSED_STAGE`**: a named build stage no later `FROM`
  or `COPY --from=` ever uses, and not the final stage: wasted build
  time. Inheriting from an earlier stage (`FROM base AS final`) counts
  as a use, so the usual `base`/`build`/`publish`/`final` chain is clean.
- **`DOCKERFILE_EXPENSIVE_RUN`**: a `RUN` matching a well-documented
  layer-bloating pattern (`apt-get install` without
  `--no-install-recommends`, `apt-get update` without cleaning the apt
  lists in the same `RUN`, `pip install` without `--no-cache-dir`,
  `npm install` vs `npm ci`, a `curl`/`wget` download left in the
  layer).
- **`DOCKERFILE_COMBINABLE_RUN`**: consecutive `RUN` instructions that
  could be one, to save per-layer overhead.

**Status: v0.1, early.** Built as a companion channel to Gap Hunter
Labs' `dockerfile-missing-user-companion`,
`dockerfile-unused-stage-companion` and `dockerfile-layer-size-companion`
IntelliJ plugins: same detection logic, manually and deliberately
ported into standalone, IntelliJ-free scanners (see each file header
for why it's a copy, not a shared module), packaged for teams that
don't use a JetBrains IDE, or want the checks in CI rather than (or in
addition to) the editor.

It is not, and does not claim to be, a full Dockerfile linter like
hadolint. Every finding is reported at `warning` level and counts
toward the exit code; use `--no-fail` (or the action's
`fail-on-find: false`) if you want the hygiene checks to report without
failing the build.

## Why a manual port instead of one shared module?

This project and the IntelliJ plugins build independently: one is a
plain JVM app, the others depend on the IntelliJ Platform Gradle
plugin. Sharing the scanners as a real Gradle module was possible but
not done for v0.1; keeping the copies in sync by hand is a known,
deliberate trade-off, not an oversight. If you change the detection
rules in one place, mirror the change in the other.

The byte-size *estimate* that `dockerfile-layer-size-companion` shows
inline in the editor is deliberately **not** ported here: it's an
estimate that needs the real build context on disk, and a per-`COPY`
size note is editor feedback, not a CI finding. Only that plugin's
RUN-pattern checks (clear best-practice violations) cross over to this
CLI.

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

Checks: DOCKERFILE_MISSING_USER, DOCKERFILE_UNUSED_STAGE,
        DOCKERFILE_EXPENSIVE_RUN, DOCKERFILE_COMBINABLE_RUN

Exit codes: 0 = no findings (or --no-fail), 1 = findings reported, 2 = usage/path error.
```

Files are recognised as Dockerfiles by name (`Dockerfile`,
`*.dockerfile`, `Dockerfile.<tag>`); `Dockerfile.md` and similar
documentation files are deliberately skipped.

Example:

```bash
$ java -jar dockerfile-audit-cli-0.1.0.jar ./my-repo
my-repo/Dockerfile:1:1: [DOCKERFILE_UNUSED_STAGE] Build stage 'builder' is never used by a later FROM or COPY --from and is not the final stage -- wasted build time
my-repo/Dockerfile:4:1: [DOCKERFILE_MISSING_USER] Final build stage has no non-root USER instruction -- the container runs as root by default
my-repo/Dockerfile:5:1: [DOCKERFILE_EXPENSIVE_RUN] apt-get install without --no-install-recommends ... | apt-get update without rm -rf /var/lib/apt/lists/* in the SAME RUN ...

dockerfile-audit-cli: 3 finding(s) in ./my-repo
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
`github/codeql-action/upload-sarif`, which needs `security-events: write`
and Code Scanning available on the repo; public repos have this free,
private repos need GitHub Advanced Security). Outputs: `finding-count`,
`sarif-file`.

**Known limitation, honestly noted:** the action builds this project
from source on every run (see [`action.yml`](action.yml)) rather than
downloading a pre-built release jar: simple for v0.1, but it costs
every consumer a Gradle build. Publishing tagged release jars and
switching the action to download one is a real follow-up, not done
yet. The action itself has been reviewed against GitHub Actions'
documented composite-action behavior but has **not yet been run in a
live GitHub Actions workflow**; treat it as unverified in that
specific environment until it has.

## What this tool is not

It reasons over a Dockerfile's real multi-stage structure as plain
text, not a full Docker parser. Known blind spots, stated honestly:

- **Missing USER:** a `USER` split across lines with a trailing `\`
  isn't recognised (single-line match only); it never checks the
  referenced user actually exists; and it can't see `USER` baked into
  an external base image pulled from a registry (no network call, since that
  would mean inspecting the image).
- **Unused stage:** a stage used only via an explicit `docker build
  --target=<name>` (not visible in the file) is flagged as a false
  positive: the tool can't see build invocations outside the
  Dockerfile.
- **Expensive RUN:** each check is a coarse substring/regex match on
  the command text, not a shell parse. It catches the common,
  well-documented cases, and will miss a pattern expressed in an
  unusual way (and could, in principle, over-match an unusual command
  that merely looks like one).

It's meant to catch the common, careless case cheaply, not to replace
a dedicated container-hardening product for anything security-critical.

## Privacy

See [PRIVACY.md](PRIVACY.md). Short version: this tool makes zero
network calls, reads only the files you point it at, and sends
nothing anywhere.

## Support this project

If this tool saved you time, a star on GitHub helps other developers find it: https://github.com/GapHunterLabs/dockerfile-audit-cli

If you want to help keep it free and maintained, you can buy me a coffee:

[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-ffdd00?style=flat&logo=buy-me-a-coffee&logoColor=black)](https://www.buymeacoffee.com/gaphunterl1)

## License

Apache License 2.0. See [LICENSE](LICENSE).
