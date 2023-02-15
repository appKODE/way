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
      nodeFactory = object : LoginNodeBuilder.Factory {
        override fun createFlowNode() = flowNode.get()
        override fun createPermissionsNodeBuilder() = loginFlowComponent.permissionsFlowComponent().nodeBuilder()
        override fun createCredentialsNode() = credentialsNode.get()
        override fun createOtpNode() = otpNode.get()
      },
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
  fun providesSchema(
    loginFlowComponent: LoginFlowComponent
  ): Schema {
    return LoginSchema(loginFlowComponent.permissionsFlowComponent().schema())
  }
}
