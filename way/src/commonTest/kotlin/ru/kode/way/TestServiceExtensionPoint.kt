package ru.kode.way

class TestServiceExtensionPoint<R : Any>(
  private val preTransition: (service: NavigationService<R>, event: Event, state: NavigationState) -> Unit,
  private val postTransition: (service: NavigationService<R>, event: Event, state: NavigationState) -> Unit,
) : ServiceExtensionPoint<R> {

  override fun onPreTransition(service: NavigationService<R>, event: Event, state: NavigationState) {
    preTransition(service, event, state)
  }

  override fun onPostTransition(service: NavigationService<R>, event: Event, state: NavigationState) {
    postTransition(service, event, state)
  }
}
