package ru.kode.way.sample.compose.permissions.routing.di

import dagger.Subcomponent
import ru.kode.way.sample.compose.permissions.routing.PermissionsNodeBuilder

@Subcomponent(modules = [PermissionsFlowModule::class])
@PermissionsScope
interface PermissionsFlowComponent {
  fun nodeFactory(): PermissionsNodeBuilder.Factory
}
