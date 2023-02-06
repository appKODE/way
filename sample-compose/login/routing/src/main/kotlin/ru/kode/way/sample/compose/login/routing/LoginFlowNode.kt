package ru.kode.way.sample.compose.login.routing

import ru.kode.way.FlowNode
import ru.kode.way.FlowTransition
import ru.kode.way.NavigateTo
import ru.kode.way.Stay
import ru.kode.way.Target
import ru.kode.way.sample.compose.login.domain.LoginService
import ru.kode.way.sample.compose.login.ui.routing.LoginFlowEvent
import javax.inject.Inject

class LoginFlowNode @Inject constructor(
  private val service: LoginService,
) : FlowNode<LoginFlowEvent, LoginFlowResult> {

  override val initial = Target.login.credentials
  override val dismissResult = LoginFlowResult.Dismissed

  override fun transition(event: LoginFlowEvent): FlowTransition<LoginFlowResult> {
    return when (event) {
      LoginFlowEvent.CredentialsReady -> NavigateTo(Target.login.otp)
      LoginFlowEvent.OtpError -> NavigateTo(Target.login.credentials)
      LoginFlowEvent.OtpSuccess -> {
        println("TODO navigate to permissions")
        Stay
      }
    }
  }

  override fun onEntry() {
    service.start()
  }

  override fun onExit() {
    service.stop()
  }
}
