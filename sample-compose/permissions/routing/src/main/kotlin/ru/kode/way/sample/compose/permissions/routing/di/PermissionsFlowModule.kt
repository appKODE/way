package ru.kode.way.sample.compose.permissions.routing.di

import dagger.Module
import dagger.Provides
import ru.kode.way.sample.compose.permissions.domain.PermissionsService
import ru.kode.way.sample.compose.permissions.routing.IntroNode
import ru.kode.way.sample.compose.permissions.routing.PermissionsFlowNode
import ru.kode.way.sample.compose.permissions.routing.PermissionsNodeBuilder
import ru.kode.way.sample.compose.permissions.routing.RequestNode
import javax.inject.Provider
import javax.inject.Scope

@Scope
annotation class PermissionsScope

@Module
object PermissionsFlowModule {
  @Provides
  @PermissionsScope
  fun provideNodeFactory(
    flowNode: Provider<PermissionsFlowNode>,
    introNode: Provider<IntroNode>,
    requestNode: Provider<RequestNode>,
  ): PermissionsNodeBuilder.Factory {
    return object : PermissionsNodeBuilder.Factory {
      override fun createRootNode() = flowNode.get()
      override fun createIntroNode() = introNode.get()
      override fun createRequestNode() = requestNode.get()
    }
  }

  @Provides
  @PermissionsScope
  fun providePermissionsService(): PermissionsService {
    return PermissionsService()
  }
}
