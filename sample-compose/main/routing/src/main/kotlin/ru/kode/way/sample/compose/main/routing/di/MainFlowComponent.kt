package ru.kode.way.sample.compose.main.routing.di

import dagger.Subcomponent
import ru.kode.way.NodeBuilder
import ru.kode.way.Schema
import javax.inject.Named

@Subcomponent(modules = [MainFlowModule::class])
@MainScope
interface MainFlowComponent {
  @Named("main")
  fun nodeBuilder(): NodeBuilder
  @Named("main")
  fun schema(): Schema
}
