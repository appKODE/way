package ru.kode.way.sample.compose.permissions.routing.di

import dagger.Subcomponent
import ru.kode.way.NodeBuilder
import ru.kode.way.sample.compose.permissions.routing.PermissionsSchema
import javax.inject.Named

@Subcomponent(modules = [PermissionsFlowModule::class])
@PermissionsScope
interface PermissionsFlowComponent {
  @Named("permissions")
  fun nodeBuilder(): NodeBuilder
  fun schema(): PermissionsSchema
}
