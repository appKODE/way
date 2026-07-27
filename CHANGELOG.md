# Changelog

## 0.9.9 - 2026-07-31

* Add flavor-aware `.dot` file routing to `way-gradle-plugin`: a product flavor (or build type, or
  flavor+buildType variant) can override any `.dot` file from a lower-priority source set by
  placing a file at the same relative path — full-file replacement, same precedence Android uses
  for resources (`main` < flavor < buildType < variant-specific)
  * Per-variant `generate<Variant>WayClasses` tasks are registered only when a variant actually
    resolves to a distinct file set, so single-flavor and non-Android projects keep the existing
    cheap single-task path
  * Variant-specific-only overrides (e.g. `src/googleDebug/way/`) are detected via filesystem
    scanning at the point AGP's DSL source set container is still incomplete, avoiding the
    timing gap where such overrides would otherwise be silently ignored
  * Blank `.dot` files no longer NPE in the ANTLR parser; empty/unmatched override directories
    surface a clear warning or validation error instead of silently generating nothing
* Add a flavor-dot-routing sample to `sample-compose:app:routing` (`google`/`huawei` flavors) with
  matching flavor-specific hand-written `FlowNode` implementations and unit tests proving both
  flavors' generated code compiles and behaves correctly

## 0.9.8 - 2026-07-07

The headline of this release is **parallel navigation**, built on the W3C SCXML statechart
model, together with node lifecycle management, transaction-style transitions, and an expanded
Compose integration. Parallel support is entirely new — nothing about it changes existing
single-flow code. A few non-parallel APIs do change; see **Breaking** at the end.

### Parallel navigation (new)

- `ParallelFlowNode<R : Any>` — a new node type (an `abstract class`) whose sub-regions are all
  **active concurrently** (SCXML AND-states). It can be the schema root or nested inside a
  region, and returns `FlowTransition<R>` — the same transition set as a regular flow.
- DOT: declare one with `type = "parallelFlow"` (omitted `resultType` defaults to `kotlin.Unit`).
- **App-owned Back / focus.** The library stores no "current"/"focused" region — a
  `ParallelFlowNode` subclass holds its own field and uses it for both rendering and Back. Return
  `DispatchBackTo(regionId): FlowTransition<Nothing>` from `transition(Event.Back)` to route the
  structural back-pop into a region; return `Ignore` for the deepest-region default. A
  stale/unknown region id soft-falls-back to the deepest region — Back never throws.
- Relative `FlowTarget` / `ScreenTarget` returned from a parallel node resolve against the
  **first** declared sub-region; target a specific region with `AbsoluteTarget`.
- `NavigationState` gains `rootNode` / `rootNodePath` so consumers (notably `way-compose`) can
  find the active root — including a parallel root — without re-walking the schema.
- New `@CrossRegionEvent` annotation marks events every region must react to (auth expired, deep
  link, config change); child flows must `Ignore` such events so they bubble to the parallel.

```kotlin
class MainTabsNode : ParallelFlowNode<Unit>(), ComposableNode {
  override val dismissResult = Unit
  var currentTab: RegionId by mutableStateOf(homeRegionId); private set  // app owns "current tab"
  override fun transition(event: Event) = when (event) {
    is TabSelected -> { currentTab = event.regionId; Stay }
    is Event.Back  -> DispatchBackTo(currentTab)   // Back follows the visible tab
    else -> Ignore
  }
}
```

### Statechart / SCXML engine + history (new)

- A canonical SCXML entry/exit/LCCA transition engine underpins the runtime, with SCXML
  conformance tests. Flows are compound (OR) states, parallels are AND-states, screens are atomic.
- **History** — new `HistoryTarget(path, deep = false)` target plus `type = "history"` (shallow)
  and `type = "deepHistory"` DOT pseudostates. Shallow restores which child was active (reset to
  its default leaf); deep restores the exact leaf. Works across parallel regions and cold
  re-entry, with typed history accessors emitted by codegen.

### Node lifecycle (new)

- `Node.onDispose()` — optional teardown hook (default no-op) on every node type.
- `NavigationService.cleanDispose()` walks alive nodes leaf-to-root calling `onDispose()` before
  shutting down; `dispose()` hard-cuts listeners/scheduler and marks the service disposed.
  Post-dispose `sendEvent` is a safe no-op.

### Transaction-style transitions (new)

- Every transition snapshots `NavigationState` and rolls back atomically if any step (node build,
  payload computation, schema validation) throws — no half-applied state is ever visible to
  listeners.
- New `validateSchema: Boolean = true` flag validates the resulting state against the schema
  after each transition; set `false` only in tests that intentionally build off-schema states.
- Payloads persist in navigation state and are pruned in lockstep with alive paths, so a
  parameterized child flow rebuilt without a fresh `NavigateTo` no longer crashes with
  `no payload for …`.

### Transitions

- `EnqueueEvent` gains `NavigateAndEnqueue` and the `NavigateTo(...) thenEnqueue event` infix DSL
  — navigate and queue a follow-up event to dispatch after the navigation completes.

### Compose (`way-compose`)

- A parallel node renders by implementing `ComposableNode` — its `Content()` lays out the parallel
  (tab bar, pager, …) and calls `NodeHost(regionId)` per sub-region, each in its own
  `rememberSaveableStateHolder`, so per-node UI state survives sibling switches. `NodeHost` renders
  parallel roots directly.
- `NodeHost(regionId)` overload renders a single region of a parallel node.
- `LocalNavigationService` (a `compositionLocalOf`) and `LocalNodePath` let descendants read the
  current service and enclosing node path without prop-drilling.
- `NodeHost`'s `AnimatedContent` is keyed by node **`Path`**, avoiding a same-path re-entry crash
  and a leak that would otherwise retain every exited node (its DI scope, view models, children)
  in the SlotTable.
- `NodeHost(nodeBuilder, onFinishRequest)` now disposes the service it owns (previously leaked it)
  and reads `onFinishRequest` through `rememberUpdatedState`; `SaveableStateProvider` keys use
  full, cross-schema-unique segment ids.

### Codegen / DOT plugin

- Segment ids are unified as `nodeId@graphId:file` (via a `SchemaRegistry` that resolves imported
  nodes to the child schema's identity), so parent and child emit the same id at a module
  boundary and path comparisons work across Gradle modules.
- Generated `*Schema` / `*NodeBuilder` expose typed `val <name>RegionId: RegionId` accessors, and
  parallel-rooted schemas emit a typed region `enum class` (`RegionEnumCodegen`).
- Build-time schema validation rejects unknown targets, mis-nested regions, duplicate segment ids,
  disconnected cycles, and codegen output-filename collisions with a precise location.
- Nullable payload/result types (`parameterType = "kotlin.String?"`) now generate `String?`.

### Breaking (vs 0.9.7)

- `NavigateTo.targets` is now `List<Target>` (was `Set<Target>`) — replace `setOf(...)` with
  `listOf(...)`; later targets in the same region win.
- `NavigationService.start()` throws on a second call; `sendEvent()` throws if called before
  `start()` (previously an opaque NPE / silent no-op).
- `NodeBuilder.invalidateCache(path: Path)` → `invalidateCache(alivePaths: Set<Path>)` — retains
  any cache entry that is a prefix of an alive path across **all** regions (fixes sibling-region
  eviction). Affects custom NodeBuilder / test-double implementations only.
- `NavigationState` gains required `rootNode` / `rootNodePath` (affects code constructing it
  directly — usually only tests).
- `way-compose`: the `NodeHost` `transitionSpec` receiver is now
  `AnimatedContentTransitionScope<Path?>` (was over `NodeWithPath?`).
- Re-run the `way-gradle-plugin` codegen: the segment-id format and new region enums change
  generated output.

## 0.9.7 - 2026-03-31

* Fix Windows-incompatible Android source discovery in `way-gradle-plugin` by avoiding `SourceDirectorySet.directories` snapshot/provider placeholder paths (e.g. `provider(?)`)
* Rework source input wiring to stay provider/lazy-based until task execution, resolving only real source directories for generation
* Keep existing generation contract and generated output layout while restoring reliable `*Schema`/`*NodeBuilder` generation across Android modules

## 0.9.6 - 2026-03-26

* Rework `way-gradle-plugin` Android source wiring to use typed Gradle/AGP APIs instead of reflection-based source set registration
* Fix generated Way classes visibility for AGP 8.x/9.x Android modules by registering generated sources in Kotlin `main` source set when variant Kotlin sources are unavailable
* Ensure routing/debug modules no longer fail with unresolved generated `*Schema`/`*NodeBuilder` classes in consuming projects
* Add regression tests for source resolution and generated directory registration in plugin test suite

## 0.9.5 - 2026-03-26

* Pin `way-gradle-plugin` compilation/publication to Java 11 (`org.gradle.jvm.version=11`) to keep compatibility with Java 11/17 consuming builds
* Fix `NavigationServiceTest` setup for `Stay` consumption by wiring screen transitions explicitly
* Regenerate and update `way-gradle-plugin` golden snapshots to match the current generator contract

## 0.9.4 - 2026-03-26

* Update build stack and dependencies: Gradle 9.4.1, Kotlin 2.3.20, AGP 9.0.1, Compose 1.10.6, Dokka 2.1.0, KSP 2.3.6, and related libs
* Rework `way-gradle-plugin` code generation wiring for new Gradle/Kotlin APIs and KSP task integration
* Add configuration cache support for code generation tasks
* Migrate Dokka integration to V2 mode and exclude ANTLR generated sources from Javadoc
* Add GitHub Actions workflows for CI validation and tag-based release automation
* Fix formatting and lint issues after Spotless/ktlint updates
* Migrate `way` and `way-compose` publication to `com.vanniktech.maven.publish` and remove legacy root publication wiring

## 0.9.3 - 2026-01-12

* Hotfix release with fixed file stream leak

## 0.9.2 - 2024-07-17

* Hotfix release with properly rebased commits

## 0.9.1 - 2024-07-17

* Add `event` parameter to `onEntry` and `onExit` callbacks of `Node`

## 0.9.0 - 2024-03-12

* Reorganize flow finish handling. Instead of specifying flow finish handler along with the target FlowNodes must now explicitly handle finish events which are sent by the child flows. Child finish event request classes are generated by the library. See sample project for examples
* Add preliminary support for `AbsoluteTarget`s. A helpers for generating such targets will be added in the next release, after testing

## 0.8.11 - 2024-02-21

* Remove explicit Schema parameter from NavigationService. It is now a public property of `NodeBuilder`
* Fix issues with EnqueueEvent which was not always working

## 0.8.10 - 2023-11-01

* Fix issue with incorrect target resolution. Make code generator to generate segments having have a unique id (internally)

## 0.8.9 - 2023-10-16

* Fix issue with incorrect target resolution. Make code generator to generate segments having have a unique id (internally)

## 0.8.8 - 2023-10-05

* Add `Path.endsWith(other: Path)`
* Rename `onFinish` → `onFinishRequest`
* Fix issue which caused publishing jar to include code generated for tests

## 0.8.7 - 2023-09-12

* Update to kotlin 1.9.10 
* Update `way-compose` to Compose 1.5.1

## 0.8.6 - 2023-08-10

* Generated NodeBuilders now have an improved caching mechanism which is coupled with the correct cache invalidation: no more create-once-use-forever child nodes
* Fixed few bugs with Stay transition

## 0.8.5 - 2023-08-01

* Generated node builder factories now have an argument corresponding to the flow parameter (if any)

## 0.8.4 - 2023-06-28

* Improve NodeHost: optional starting, make utility functions public. This will aid in cases where one wishes to build their own NodeHost

## 0.8.3 - 2023-05-03

* Add support for kotlin-jvm projects to gradle plugin
* Rename `NodeFactory.createFlowNode()` to `NodeFactory.createRootNode()`

## 0.8.2 - 2023-03-07

* Add support for extending nodes through composition: provide `NodeExtensions` mechanism
* Add basic support for animated transitions in `NodeHost` for `way-compose` 
* Implement node hooks as one of `NodeExtension`s. Use `BaseFlowNode`, `BaseScreenNode` classes to take advantage of node hooks 
* Remove excessive logging in `way-gradle-plugin`

## 0.8.1 - 2023-03-01

* Fix issues with running `testDebugUnitTest` in consuming projects. Source sets were incorrectly set up by `way-gradle-plugin` for android projects.


## 0.8.0 - 2023-02-28

* Initial release
