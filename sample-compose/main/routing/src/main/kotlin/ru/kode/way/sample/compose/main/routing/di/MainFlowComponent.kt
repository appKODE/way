package ru.kode.way.sample.compose.main.routing.di

import dagger.Subcomponent
import ru.kode.way.sample.compose.main.routing.MainNodeBuilder

@Subcomponent(modules = [MainFlowModule::class])
@MainScope
interface MainFlowComponent {
  fun nodeFactory(): MainNodeBuilder.Factory
}
