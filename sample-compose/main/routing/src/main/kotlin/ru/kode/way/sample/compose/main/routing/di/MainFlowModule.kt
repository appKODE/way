package ru.kode.way.sample.compose.main.routing.di

import dagger.Module
import dagger.Provides
import ru.kode.way.sample.compose.main.routing.HomeNode
import ru.kode.way.sample.compose.main.routing.MainFlowNode
import ru.kode.way.sample.compose.main.routing.MainNodeBuilder
import javax.inject.Provider
import javax.inject.Scope

@Scope
annotation class MainScope

@Module
object MainFlowModule {
  @Provides
  @MainScope
  fun provideNodeBuilder(
    flowNode: Provider<MainFlowNode>,
    homeNode: Provider<HomeNode>,
  ): MainNodeBuilder.Factory {
    return object : MainNodeBuilder.Factory {
      override fun createFlowNode() = flowNode.get()
      override fun createHomeNode() = homeNode.get()
    }
  }
}
