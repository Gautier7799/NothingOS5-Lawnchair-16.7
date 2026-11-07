# Task report template

Copy this to `reports/<TASK_ID>.md` when publishing a result. Replace every
`<...>`. Do not delete sections — write `none` instead, so a reader can tell the
difference between "nothing to report" and "forgotten".

---

# <TASK_ID> — <title>

| Field | Value |
| --- | --- |
| Status | <ACCEPTED / RUNNING / REVIEW_REQUIRED / COMPLETED / BLOCKED> |
| Owner | <chatgpt / openhands / human> |
| Risk | <read-only / write / destructive / scope-change> |
| Branch | `<branch>` |
| Commit | `<full sha>` |
| Updated | `<ISO-8601 UTC>` |
| Issue | <#number or none> |

## Objective

<one or two sentences: what was asked>

## What changed

| File | Change |
| --- | --- |
| `<path>` | <added / modified / deleted, and why> |

## Evidence

Every claim below is a command that was actually run and its actual result.

```
$ <command>
<result>
```

```
$ <command>
<result>
```

## Test results

| Suite | Command | Result |
| --- | --- | --- |
| <name> | `<command>` | <n passed, n failed> |

## Build artifacts

| Artifact | Size | SHA-256 |
| --- | --- | --- |
| `<path>` | <bytes> | `<hash>` |

## Unresolved issues

- <issue, or write "none">

## Notes for the reviewer

<what to check, what to be careful about, what was deliberately not done>
