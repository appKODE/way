package ru.kode.way.sample.compose.login.routing.di

import dagger.Subcomponent
import ru.kode.way.sample.compose.login.routing.LoginNodeBuilder
import ru.kode.way.sample.compose.permissions.routing.di.PermissionsFlowComponent

@Subcomponent(modules = [LoginFlowModule::class])
@LoginScope
interface LoginFlowComponent {
  fun nodeFactory(): LoginNodeBuilder.Factory
  fun permissionsFlowComponent(): PermissionsFlowComponent
}
