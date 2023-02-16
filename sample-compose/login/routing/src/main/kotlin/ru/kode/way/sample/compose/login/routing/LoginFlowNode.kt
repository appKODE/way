package ru.kode.way.sample.compose.login.routing

import ru.kode.way.Event
import ru.kode.way.Finish
import ru.kode.way.FlowNode
import ru.kode.way.FlowTransition
import ru.kode.way.NavigateTo
import ru.kode.way.Target
import ru.kode.way.sample.compose.login.domain.LoginService
import ru.kode.way.sample.compose.login.ui.routing.LoginFlowEvent
import ru.kode.way.sample.compose.permissions.routing.PermissionsFlowResult
import ru.kode.way.whenFlowEvent
import javax.inject.Inject

class LoginFlowNode @Inject constructor(
  private val service: LoginService,
) : FlowNode<LoginFlowResult> {

  override val initial = Target.login.credentials
  override val dismissResult = LoginFlowResult.Dismissed

  override fun transition(event: Event): FlowTransition<LoginFlowResult> {
    return event.whenFlowEvent { e: LoginFlowEvent ->
      when (e) {
        LoginFlowEvent.CredentialsReady -> NavigateTo(Target.login.otp)
        LoginFlowEvent.OtpError -> NavigateTo(Target.login.credentials)
        LoginFlowEvent.OtpSuccess -> {
          NavigateTo(
            Target.login.permissions { result: PermissionsFlowResult ->
              when (result) {
                PermissionsFlowResult.AllGranted -> Finish(LoginFlowResult.Success)
                PermissionsFlowResult.PartiallyGranted -> Finish(LoginFlowResult.Success)
                PermissionsFlowResult.Dismissed -> NavigateTo(Target.login.credentials)
                PermissionsFlowResult.Denied -> NavigateTo(Target.login.credentials)
              }
            }
          )
        }
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
