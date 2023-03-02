package ru.kode.way.sample.compose.app.routing.di

import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import ru.kode.way.FlowNode
import ru.kode.way.NodeBuilder
import ru.kode.way.sample.compose.app.routing.AppFlowNode
import ru.kode.way.sample.compose.app.routing.AppNodeBuilder
import ru.kode.way.sample.compose.login.routing.LoginFlow
import ru.kode.way.sample.compose.login.routing.di.LoginFlowComponent
import ru.kode.way.sample.compose.main.routing.MainFlow
import ru.kode.way.sample.compose.main.routing.di.MainFlowComponent
import javax.inject.Provider
import javax.inject.Scope

@Scope
annotation class AppFlowScope

@Subcomponent(modules = [AppFlowModule::class])
@AppFlowScope
interface AppFlowComponent {
  fun loginFlowComponent(): LoginFlowComponent
  fun mainFlowComponent(): MainFlowComponent

  fun nodeFactory(): AppNodeBuilder.Factory
}

@Module
object AppFlowModule {

  @Provides
  @AppFlowScope
  fun provideNodeFactory(component: AppFlowComponent, appFlowNode: Provider<AppFlowNode>): AppNodeBuilder.Factory {
    return object : AppNodeBuilder.Factory {

      override fun createFlowNode(): FlowNode<*> = appFlowNode.get()
      override fun createMainNodeBuilder(): NodeBuilder = MainFlow.nodeBuilder(component.mainFlowComponent())
      override fun createLoginNodeBuilder(): NodeBuilder = LoginFlow.nodeBuilder(component.loginFlowComponent())
    }
  }
}
