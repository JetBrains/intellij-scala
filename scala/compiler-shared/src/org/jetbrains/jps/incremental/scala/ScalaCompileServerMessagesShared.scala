package org.jetbrains.jps.incremental.scala

import com.intellij.AbstractBundle
import org.jetbrains.annotations.{Nls, PropertyKey}

import java.net.InetAddress
import scala.annotation.varargs
import scala.concurrent.duration.FiniteDuration

abstract class ScalaCompileServerMessagesShared(bundle: AbstractBundle) {

  //noinspection ReferencePassedToNls
  @Nls
  @varargs
  def message(@PropertyKey(resourceBundle = ScalaCompileServerMessagesShared.BUNDLE) key: String, params: Any*): String =
    bundle.getMessage(key, params: _*)

  @Nls
  def unknownHostErrorMessage(address: InetAddress): String =
    message("unknown.ip.address.of.compile.server", address.toString)

  @Nls
  def cantConnectToCompileServerErrorMessage(address: InetAddress, port: Int, reason: Option[String] = None): String = {
    @Nls val reasonSuffix = reason.fold("")(r => s" ($r)")
    //extra `toString` is caleld for port because otherwise the port will be formatted as 3,200
    //noinspection ReferencePassedToNls
    message("cannot.connect.to.compile.server.at", address.toString, port.toString) + reasonSuffix
  }

  @Nls
  def socketConnectTimeout(connectTimeout: FiniteDuration): String =
    message("socket.connect.timout", connectTimeout)
}

object ScalaCompileServerMessagesShared {
  final val BUNDLE = "messages.ScalaCompileServerSharedBundle"
}
