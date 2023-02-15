package ru.kode.way.sample.compose.permissions.routing.di

import dagger.Module
import dagger.Provides
import ru.kode.way.NodeBuilder
import ru.kode.way.Schema
import ru.kode.way.sample.compose.permissions.domain.PermissionsService
import ru.kode.way.sample.compose.permissions.routing.IntroNode
import ru.kode.way.sample.compose.permissions.routing.PermissionsFlowNode
import ru.kode.way.sample.compose.permissions.routing.PermissionsNodeBuilder
import ru.kode.way.sample.compose.permissions.routing.PermissionsSchema
import ru.kode.way.sample.compose.permissions.routing.RequestNode
import javax.inject.Named
import javax.inject.Provider
import javax.inject.Scope

@Scope
annotation class PermissionsScope

@Module
object PermissionsFlowModule {
  @Provides
  @PermissionsScope
  @Named("permissions")
  fun provideNodeBuilder(
    flowNode: Provider<PermissionsFlowNode>,
    introNode: Provider<IntroNode>,
    requestNode: Provider<RequestNode>
  ): NodeBuilder {
    return PermissionsNodeBuilder(
      nodeFactory = object : PermissionsNodeBuilder.Factory {
        override fun createFlowNode() = flowNode.get()
        override fun createIntroNode() = introNode.get()
        override fun createRequestNode() = requestNode.get()
      },
    )
  }

  @Provides
  @PermissionsScope
  fun providePermissionsService(): PermissionsService {
    return PermissionsService()
  }

  @Provides
  @PermissionsScope
  @Named("permissions")
  fun providesSchema(): Schema {
    return PermissionsSchema()
  }
}
