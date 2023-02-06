package ru.kode.way.sample.compose.login.routing.di

import dagger.Subcomponent
import ru.kode.way.NodeBuilder
import ru.kode.way.Schema
import javax.inject.Named

@Subcomponent(modules = [LoginFlowModule::class])
@LoginScope
interface LoginFlowComponent {
  @Named("login")
  fun nodeBuilder(): NodeBuilder
  @Named("login")
  fun schema(): Schema
}
