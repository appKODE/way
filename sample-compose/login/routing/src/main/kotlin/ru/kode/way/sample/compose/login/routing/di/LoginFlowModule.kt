package ru.kode.way.sample.compose.login.routing.di

import dagger.Module
import dagger.Provides
import ru.kode.way.ScreenNode
import ru.kode.way.sample.compose.login.domain.LoginService
import ru.kode.way.sample.compose.login.routing.CredentialsNode
import ru.kode.way.sample.compose.login.routing.LoginFlowNode
import ru.kode.way.sample.compose.login.routing.LoginNodeBuilder
import ru.kode.way.sample.compose.login.routing.OtpNode
import ru.kode.way.sample.compose.permissions.routing.PermissionsFlow
import ru.kode.way.sample.compose.permissions.routing.PermissionsNodeBuilder
import javax.inject.Provider
import javax.inject.Scope

@Scope
annotation class LoginScope

@Module
object LoginFlowModule {
  @Provides
  @LoginScope
  fun provideNodeFactory(
    flowNode: Provider<LoginFlowNode>,
    credentialsNode: Provider<CredentialsNode>,
    otpNode: Provider<OtpNode>,
    loginFlowComponent: LoginFlowComponent,
  ): LoginNodeBuilder.Factory {
    return object : LoginNodeBuilder.Factory {
      override fun createRootNode() = flowNode.get()
      override fun createPermissionsNodeBuilder(): PermissionsNodeBuilder {
        return PermissionsFlow.nodeBuilder(loginFlowComponent.permissionsFlowComponent())
      }
      override fun createCredentialsNode() = credentialsNode.get()
      override fun createOtpNode(maskInput: Boolean): ScreenNode {
        return otpNode.get().apply { this.maskInput = maskInput }
      }
    }
  }

  @Provides
  @LoginScope
  fun provideLoginService(): LoginService {
    return LoginService()
  }
}
