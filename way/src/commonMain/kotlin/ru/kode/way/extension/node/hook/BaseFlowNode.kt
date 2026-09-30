package ru.kode.way.extension.node.hook

import ru.kode.way.Event
import ru.kode.way.EventSink
import ru.kode.way.FlowNode
import ru.kode.way.FlowTransition
import ru.kode.way.Ignore
import ru.kode.way.NavigationService
import ru.kode.way.Path

/**
 * A basic flow node with hooks support.
 * Create your own custom node if this one is too basic or if you don't need to use hooks.
 *
 * Requires [NodeHooksSupportExtensionPoint] to be added to [NavigationService] to work.
 *
 * Subclasses may read [nodePath] inside [transition] / [onExit] / Compose `Content` to learn
 * the absolute path this node was mounted at. It is set just before the first [onEntry] and
 * remains valid for the lifetime of the node. Reading it before entry throws — use only after
 * the runtime has activated the node.
 *
 * [eventSink] sends events on behalf of this node instance.
 */
abstract class BaseFlowNode<R : Any> :
  FlowNode<R>,
  HasFlowNodeHooks<R> {
  private val _hooks = mutableListOf<FlowNodeHook<R>>()
  override val hooks: List<FlowNodeHook<R>> = _hooks

  private var _nodePath: Path? = null
  val nodePath: Path
    get() = checkNotNull(_nodePath) {
      "nodePath is not available before the runtime calls onEntry on this node"
    }

  private var _eventSink: EventSink? = null

  /**
   * This node's sink ([NavigationService.eventSink]): events are resolved from the active leaves under this flow and
   * dropped with [ru.kode.way.DropReason.StaleSource] once this node instance has left navigation, so it is safe to
   * send from async work that may outlive the node. Hand it to the presenters of the flow's children if they act on
   * behalf of the flow. Attached by the service right before every entry (usable in `onEntry` and entry hooks);
   * reading it before the first entry throws.
   */
  val eventSink: EventSink
    get() = checkNotNull(_eventSink) {
      "eventSink is not available before the runtime calls onEntry on this node"
    }

  internal fun attachEventSink(sink: EventSink) {
    _eventSink = sink
  }

  override fun onEntry(event: Event, path: Path) {
    _nodePath = path
    onEntry(event)
  }

  override fun transition(event: Event): FlowTransition<R> = Ignore

  override fun addHook(hook: FlowNodeHook<R>) {
    _hooks.add(hook)
  }

  override fun removeHook(hook: FlowNodeHook<R>) {
    _hooks.remove(hook)
  }
}
