package ru.kode.way.sample.compose.login.routing

import ru.kode.way.Event
import ru.kode.way.Finish
import ru.kode.way.FlowNode
import ru.kode.way.FlowTransition
import ru.kode.way.Ignore
import ru.kode.way.NavigateTo
import ru.kode.way.Target
import ru.kode.way.sample.compose.login.domain.LoginService
import ru.kode.way.sample.compose.login.ui.routing.LoginFlowEvent
import ru.kode.way.sample.compose.permissions.routing.PermissionsFlowResult
import javax.inject.Inject

class LoginFlowNode @Inject constructor(
  private val service: LoginService,
) : FlowNode<LoginFlowResult> {

  override val initial = Target.login.credentials
  override val dismissResult = LoginFlowResult.Dismissed

  override fun transition(event: Event): FlowTransition<LoginFlowResult> {
    return when (event) {
      is LoginFlowEvent.CredentialsReady -> NavigateTo(Target.login.otp(maskInput = true))
      is LoginFlowEvent.OtpError -> NavigateTo(Target.login.credentials)
      is LoginFlowEvent.OtpSuccess -> NavigateTo(Target.login.permissions)
      is LoginChildFinishRequest.Permissions -> {
        when (event.result) {
          PermissionsFlowResult.AllGranted -> Finish(LoginFlowResult.Success)
          PermissionsFlowResult.PartiallyGranted -> Finish(LoginFlowResult.Success)
          PermissionsFlowResult.Dismissed -> NavigateTo(Target.login.credentials)
          PermissionsFlowResult.Denied -> NavigateTo(Target.login.credentials)
        }
      }

      else -> Ignore
    }
  }

  override fun onEntry(event: Event) {
    service.start()
  }

  override fun onExit(event: Event) {
    service.stop()
  }
}
