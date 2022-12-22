package ru.kode.way

sealed interface Target {
}

data class ScreenTarget(val path: Path) : Target
data class FlowTarget<R1 : Any, R2 : Any>(val path: Path, val onFinish: (R1) -> FlowTransition<R2>) : Target
