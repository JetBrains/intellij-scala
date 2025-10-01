package org.jetbrains.plugins.scala.compiler

import org.jetbrains.jps.incremental.scala.remote.RemoteResourceOwner
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings

import java.net.InetAddress

private class RemoteServerStopper(override val port: Int) extends RemoteResourceOwner {
  override protected val address: InetAddress = InetAddress.getByName(null)

  /**
   * Stops compile server instance for current application instance (if server exists)
   */
  def sendStop(): Unit =
    try {
      val stopCommand = "stop_" + ScalaCompileServerSettings.getInstance().COMPILE_SERVER_ID
      send(stopCommand, Seq(s"--nailgun-port $port"), null)
    } catch {
      case _: Exception =>
    }
}
