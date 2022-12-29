package ru.kode.way.sample.compose.app.routing.di

import ru.kode.way.NodeBuilder
import ru.kode.way.sample.compose.permissions.routing.di.PermissionsFlowComponent

interface AppFlowComponent {
  fun nodeBuilder(): NodeBuilder
  fun permissionsFlowComponent(): PermissionsFlowComponent
}
