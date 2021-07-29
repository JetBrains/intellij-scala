package org.jetbrains.jps.incremental.scala.utils

import org.jetbrains.annotations.Nls
import org.jetbrains.jps.incremental.scala.ScalaJpsSharedBundle

import java.net.InetAddress
import scala.concurrent.duration.FiniteDuration

object CompileServerSharedConnectionErrorMessages {

  @Nls
  def unknownHostErrorMessage(address: InetAddress): String =
    ScalaJpsSharedBundle.message("unknown.ip.address.of.compile.server", address.toString)

  @Nls
  def cantConnectToCompileServerErrorMessage(address: InetAddress, port: Int, reason: Option[String] = None): String = {
    @Nls val reasonSuffix = reason.fold("")(r => s" ($r)")
    //noinspection ReferencePassedToNls
    ScalaJpsSharedBundle.message("cannot.connect.to.compile.server.at", address.toString, port) + reasonSuffix
  }

  @Nls
  def socketConnectTimeout(connectTimeout: FiniteDuration): String =
    ScalaJpsSharedBundle.message("socket.connect.timout", connectTimeout)
}
