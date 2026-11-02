# Privacy Policy — dockerfile-audit-cli

**Effective date:** 2026-11-02

dockerfile-audit-cli is a Gap Hunter Labs command-line tool. This policy
is short because the tool's design makes it short: there is nothing to
disclose beyond what's below.

## What this tool collects

**Nothing.** dockerfile-audit-cli does not collect, store, transmit, or
sell any data — no source code, no file contents, no usage analytics,
no telemetry, no crash reports, no personally identifiable information.

## What it reads

It reads the Dockerfiles under whatever path you point it at (default:
the current directory), to run its static security and hygiene checks
on them. That's the tool's whole job — nothing is read that you didn't
ask it to scan, and nothing read is written anywhere except the report
you asked for (stdout, or the file passed to `--out`).

## Network access

**None.** dockerfile-audit-cli makes zero network calls during normal
operation. Every check runs entirely in-process, against files already
on disk. Nothing you scan is ever sent anywhere.

When run as the GitHub Action in this repository, the surrounding CI
job does its own network activity (checking out your repo, downloading
the JDK, optionally uploading the SARIF report to GitHub Code Scanning
if `upload-sarif: true`) — that's GitHub Actions infrastructure and the
`upload-sarif` step you opted into, not this tool phoning home.

## Third parties

None inside the tool itself. The optional `upload-sarif` step in
`action.yml` uses `github/codeql-action/upload-sarif`, GitHub's own
first-party action — disable it (`upload-sarif: false`) if you don't
want that upload to happen.

## Changes to this policy

If this ever changes, this file will be updated and the change will be
noted in the project's release notes.

## Contact

Questions about this policy: **contact@gaphunterlabs.com**
