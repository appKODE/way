package ru.kode.way

interface ServiceExtensionPoint<R : Any> {
  /**
   * Called when service receives a new event, before it builds and executes a transition
   *
   * @param service navigation service
   * @param event event which is about to trigger a transition
   * @param state current navigation state
   */
  fun onPreTransition(service: NavigationService<R>, event: Event, state: NavigationState)
  /**
   * Called after service processed an event, built and executed a transition
   *
   * @param service navigation service
   * @param event event which has triggered the transition
   * @param state a new navigation state after transition
   */
  fun onPostTransition(service: NavigationService<R>, event: Event, state: NavigationState)
}
