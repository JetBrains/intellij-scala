package org.jetbrains.jps.incremental.scala

import java.net.InetAddress
import scala.concurrent.duration.FiniteDuration

object CompileServerCommonMessages {
  def unknownHostErrorMessage(address: InetAddress): String =
    ScalaJpsSharedBundle.message("unknown.ip.address.of.compile.server", address.toString)

  def cantConnectToCompileServerErrorMessage(address: InetAddress, port: Int): String =
    ScalaJpsSharedBundle.message("cannot.connect.to.compile.server.at", address.toString, port)

  def noResponseFromCompileServer(address: InetAddress, port: Int, readTimeout: FiniteDuration): String =
    ScalaJpsSharedBundle.message("no.response.from.the.compile.server", address.toString, port, readTimeout)
}
