# Roadmap

Status of the SCXML rework and what remains. The core is **functionally complete**: canonical
entry/exit/LCCA engine, all-regions-active parallel model, shallow + deep history (including across
parallel regions and cold re-entry), typed history codegen, app-side back routing via `DispatchBackTo`,
SCXML conformance tests, and docs. Nothing below is a broken or missing capability — these are extensions,
optional cleanups, and
a future conformance effort.

## Near-term (ready to pick up when there's a need)

### Relative-target unification
Let relative `FlowTarget`/`ScreenTarget` resolve across schema boundaries so app code rarely needs to
hand-write an `AbsoluteTarget`. `AbsoluteTarget` stays as the load-bearing engine; this only trims its
*public* role.
- **Approach:** BFS fallback in `resolveAbsoluteTargetPath` (`TargetResolution.kt`), scoped to that one
  file.
- **Value:** removes cross-schema navigation boilerplate for consumers.
- **Trigger:** first app site that would otherwise hand-write an `AbsoluteTarget`.

## Feature extensions (on demand)

### History under imported (cross-schema) flows
The parser currently accepts a history pseudostate under a plain flow or a parallel, but rejects an
**imported** flow parent (the parent flow lives in another `.dot`).
- **Cost:** non-trivial — cross-schema path resolution for the recorded configuration.
- **Trigger:** a real schema that needs to remember an imported sub-flow's configuration.

## Optional internal cleanups (low priority — no behavior change)

These re-implement already-correct, already-shipping code. They carry regression risk for **zero**
user-facing change, so do them only if the internal consistency is independently worth it.

### `synchronizeNodes` exit path via `computeExitSet`
Re-express the per-region exit diff in `NavigationService.synchronizeNodes` through the canonical
`StatechartAlgorithm.computeExitSet`. The current diff is already correct.

## Long-term — full SCXML conformance

The rework deliberately scoped these out; they are a distinct, larger effort:
- **Guards** on transitions (conditional transitions).
- **Eventless / automatic** transitions (transitions with no trigger).
- **Final states** and done events.
- **Document-order conflict resolution** when multiple transitions are simultaneously enabled.

## Decided — not doing

- **Collapse `history` / `deepHistory` into one.** They are genuinely distinct (deep restores the exact
  leaf; shallow restores which child, reset to its default — see `HistoryTargetNestedTest`). Both are
  kept and documented.
- **Remove `HistoryTarget` / hide it behind an auto-restore flag.** Kept as an explicit target: the
  teardown-and-resume use case is real and callers want per-navigation shallow-vs-deep control.
