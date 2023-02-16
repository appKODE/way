package ru.kode.way.sample.compose.app.routing.di

import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import ru.kode.way.FlowNode
import ru.kode.way.NodeBuilder
import ru.kode.way.Schema
import ru.kode.way.sample.compose.app.routing.AppFlowNode
import ru.kode.way.sample.compose.app.routing.AppNodeBuilder
import ru.kode.way.sample.compose.app.routing.AppSchema
import ru.kode.way.sample.compose.login.routing.di.LoginFlowComponent
import ru.kode.way.sample.compose.main.routing.di.MainFlowComponent
import javax.inject.Named
import javax.inject.Provider
import javax.inject.Scope

@Scope
annotation class AppFlowScope

@Subcomponent(modules = [AppFlowModule::class])
@AppFlowScope
interface AppFlowComponent {
  fun loginFlowComponent(): LoginFlowComponent
  fun mainFlowComponent(): MainFlowComponent

  @Named("app") fun nodeBuilder(): NodeBuilder
  @Named("app") fun schema(): Schema
}

@Module
object AppFlowModule {
  @Provides
  @AppFlowScope
  @Named("app")
  fun provideNodeBuilder(
    flowNode: Provider<AppFlowNode>,
    appFlowComponent: AppFlowComponent,
  ): NodeBuilder {
    return AppNodeBuilder(
      nodeFactory = object : AppNodeBuilder.Factory {
        private val loginFlowComponent = appFlowComponent.loginFlowComponent()
        private val mainFlowComponent = appFlowComponent.mainFlowComponent()

        override fun createFlowNode(): FlowNode<*> = flowNode.get()
        override fun createMainNodeBuilder(): NodeBuilder = mainFlowComponent.nodeBuilder()
        override fun createLoginNodeBuilder(): NodeBuilder = loginFlowComponent.nodeBuilder()
      }
    )
  }

  @Provides
  @AppFlowScope
  @Named("app")
  fun provideSchema(appFlowComponent: AppFlowComponent): Schema {
    return AppSchema(
      loginSchema = appFlowComponent.loginFlowComponent().schema(),
      mainSchema = appFlowComponent.mainFlowComponent().schema(),
    )
  }
}
