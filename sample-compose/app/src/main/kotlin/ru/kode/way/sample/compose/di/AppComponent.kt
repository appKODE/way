package ru.kode.way.sample.compose.di

import dagger.BindsInstance
import dagger.Component
import dagger.Module
import ru.kode.way.sample.compose.app.routing.di.AppFlowComponent
import ru.kode.way.sample.compose.core.routing.FlowEventSink
import javax.inject.Scope

@Scope
annotation class AppScope

@AppScope
@Component(modules = [AppModule::class])
interface AppComponent {
  fun appFlowComponent(): AppFlowComponent
  fun eventSink(): FlowEventSink

  @Component.Builder
  interface Builder {
    @BindsInstance
    fun eventSink(sink: FlowEventSink): Builder
    fun build(): AppComponent
  }
}

@Module
object AppModule
