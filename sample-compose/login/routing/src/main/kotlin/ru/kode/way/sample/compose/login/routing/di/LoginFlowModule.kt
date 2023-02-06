package ru.kode.way.sample.compose.login.routing.di

import dagger.Module
import dagger.Provides
import ru.kode.way.NodeBuilder
import ru.kode.way.Schema
import ru.kode.way.sample.compose.login.domain.LoginService
import ru.kode.way.sample.compose.login.routing.CredentialsNode
import ru.kode.way.sample.compose.login.routing.LoginFlowNode
import ru.kode.way.sample.compose.login.routing.LoginNodeBuilder
import ru.kode.way.sample.compose.login.routing.LoginSchema
import ru.kode.way.sample.compose.login.routing.OtpNode
import ru.kode.way.sample.compose.permissions.routing.di.PermissionsFlowComponent
import javax.inject.Named
import javax.inject.Provider
import javax.inject.Scope

@Scope
annotation class LoginScope

@Module
object LoginFlowModule {
  @Provides
  @LoginScope
  @Named("login")
  fun provideNodeBuilder(
    flowNode: Provider<LoginFlowNode>,
    credentialsNode: Provider<CredentialsNode>,
    otpNode: Provider<OtpNode>,
    loginFlowComponent: LoginFlowComponent
  ): NodeBuilder {
    return LoginNodeBuilder(
      flowNode = { flowNode.get() },
      credentialsNode = { credentialsNode.get() },
      otpNode = { otpNode.get() },
      permissionsNodeBuilder = { loginFlowComponent.permissionsFlowComponent().nodeBuilder() },
    )
  }

  @Provides
  @LoginScope
  fun provideLoginService(): LoginService {
    return LoginService()
  }

  @Provides
  @LoginScope
  @Named("login")
  fun providesSchema(): Schema {
    return LoginSchema()
  }
}
