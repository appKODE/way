package ru.kode.way

interface ServiceExtensionPoint<R : Any> {
  /**
   * Called when service receives a new event, before it builds and executes a transition. The event has already been
   * resolved at this point (the nodes' `transition()` have been consulted), so an event dropped for a missing payload
   * does not reach this method.
   *
   * @param service navigation service
   * @param event event which is about to trigger a transition
   * @param state current navigation state
   */
  fun onPreTransition(service: NavigationService<R>, event: Event, state: NavigationState)

  /**
   * Called after service processed an event, built and executed a transition.
   * The transition is fully committed before this method is called — navigation state,
   * alive stacks, and node lifecycle calls ([Node.onEntry]/[Node.onExit]) are all final.
   *
   * If this method throws, the exception propagates to the caller of [NavigationService.send]
   * but navigation state is **not** rolled back. Guard any error-prone work with `try/catch`
   * inside your implementation.
   *
   * @param service navigation service
   * @param event event which has triggered the transition
   * @param state a new navigation state after transition
   */
  fun onPostTransition(service: NavigationService<R>, event: Event, state: NavigationState)

  /**
   * Called when service drops [event] instead of applying its transition, after the state has been rolled
   * back and before [EventDroppedException] is thrown in [NavigationService.strictEventDropping] mode.
   * Neither [onPreTransition] nor [onPostTransition] is called for a dropped event. The one exception is a payload
   * missed by a [Schema] which does not report the node as parameterized (a hand-rolled schema): it is only detected
   * when the node builder throws [MissingPayloadException] while building nodes, after [onPreTransition].
   *
   * If this method throws, the exception propagates out of [NavigationService.send]; the state stays rolled
   * back and the events still enqueued are kept and processed by the next [NavigationService.send].
   *
   * @param service navigation service
   * @param event event which was dropped
   * @param reason why the event was dropped
   */
  fun onEventDropped(service: NavigationService<R>, event: Event, reason: DropReason) {}
}
