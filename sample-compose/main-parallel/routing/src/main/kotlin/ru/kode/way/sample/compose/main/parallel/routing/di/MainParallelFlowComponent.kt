package ru.kode.way.sample.compose.main.parallel.routing.di

import dagger.Subcomponent
import ru.kode.way.sample.compose.main.parallel.routing.head.di.HeadFlowNodeFactory

@Subcomponent(modules = [MainParallelFlowModule::class])
@MainScope
interface MainParallelFlowComponent {
  fun nodeFactory(): MainParallelFlowNodeFactory
  fun headNodeFactory(): HeadFlowNodeFactory
}
