package org.jetbrains.plugins.scala.compiler

import java.nio.charset.StandardCharsets
import java.util.Base64

import com.intellij.credentialStore.{CredentialAttributes, Credentials}
import com.intellij.ide.passwordSafe.PasswordSafe

/**
  * @author Maris Alexandru
  */
object HydraCredentialsManager {
  private val passwordSafe = PasswordSafe.getInstance()
  private val ServiceName = "Hydra"
  private val CredentialAttributes = new CredentialAttributes(ServiceName)
  private val DefaultUsername = ""
  private val DefaultPassword = ""

  def getLogin: String = {
    val credentials = passwordSafe.get(CredentialAttributes)
    if (credentials == null) {
      DefaultUsername
    } else {
      credentials.getUserName
    }
  }

  def setCredentials(login: String, password: String) = passwordSafe.set(CredentialAttributes, new Credentials(login, password))

  def getPlainPassword: String = {
    val credentials = passwordSafe.get(CredentialAttributes)
    if(credentials == null) {
      DefaultPassword
    } else {
      credentials.getPasswordAsString
    }
  }

  def getBasicAuthEncoding(): String = {
    new String(encode(s"${getLogin}:${getPlainPassword}"))
  }

  private def encode(text: String) = Base64.getEncoder.encodeToString(text.getBytes(StandardCharsets.UTF_8))
}
