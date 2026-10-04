package ru.kode.way.extension.node.hook

import ru.kode.way.Event
import ru.kode.way.EventSink
import ru.kode.way.Ignore
import ru.kode.way.NavigationService
import ru.kode.way.ScreenNode
import ru.kode.way.ScreenTransition

/**
 * A basic screen node with hooks support.
 * Create your own custom node if this one is too basic or if you don't need to use hooks.
 *
 * Requires [NodeHooksSupportExtensionPoint] to be added to [NavigationService] to work.
 *
 * Unlike [BaseFlowNode] there is no `nodePath`: only flow nodes need their absolute mount point (to
 * translate schema-local sibling RegionIds to absolute paths), so screens deliberately omit it.
 */
abstract class BaseScreenNode :
  ScreenNode,
  HasScreenNodeHooks {
  private val _hooks = mutableListOf<ScreenNodeHook>()
  override val hooks: List<ScreenNodeHook> = _hooks

  private var _eventSink: EventSink? = null

  /**
   * This screen's sink ([NavigationService.eventSink]): events are resolved from this screen and dropped with
   * [ru.kode.way.DropReason.StaleSource] once this node instance has left navigation. A non-Back event nobody handles
   * in its scope falls back to the whole tree, like `send`. The service attaches a new
   * sink right before every entry (usable in `onEntry` and entry hooks), so if the node builder returns the same
   * instance on re-entry, reading this property later yields the sink of the new entry. For async work, and when
   * handing it to the screen's presenter / ViewModel, capture it first (`val sink = eventSink` in `onEntry`, or before
   * launching the work): the captured sink goes stale when this entry ends. Events it sends from `onEntry` of a
   * transition which is then rolled back are discarded without [ru.kode.way.ServiceExtensionPoint.onEventDropped]
   * (that entry never happened). Reading it before the first entry throws.
   */
  val eventSink: EventSink
    get() = checkNotNull(_eventSink) {
      "eventSink is not available before the runtime calls onEntry on this node"
    }

  internal fun attachEventSink(sink: EventSink) {
    _eventSink = sink
  }

  override fun transition(event: Event): ScreenTransition = Ignore

  override fun addHook(hook: ScreenNodeHook) {
    _hooks.add(hook)
  }

  override fun removeHook(hook: ScreenNodeHook) {
    _hooks.remove(hook)
  }
}
