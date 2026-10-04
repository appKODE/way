package ru.kode.way.sample.compose.di

import dagger.Component
import dagger.Module
import ru.kode.way.sample.compose.app.routing.di.AppFlowComponent
import javax.inject.Scope

@Scope
annotation class AppScope

@AppScope
@Component(modules = [AppModule::class])
interface AppComponent {
  fun appFlowComponent(): AppFlowComponent
}

@Module
object AppModule
