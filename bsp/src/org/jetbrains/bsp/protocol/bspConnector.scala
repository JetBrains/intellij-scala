package org.jetbrains.bsp.protocol

import java.io.File
import java.net.URI

import ch.epfl.scala.bsp.InitializeBuildParams
import monix.eval.Task
import org.jetbrains.bsp.{BspError, BspErrorMessage}
import org.jetbrains.bsp.protocol.BspServerConnector.BspConnectionMethod


abstract class BspServerConnector(initParams: InitializeBuildParams) {
  /**
    * Connect to a bsp server with one of the given methods.
    * @param methods methods supported by the bsp server, in order of preference
    * @return None if no compatible method is found. TODO should be an error response
    */
  def connect(methods: BspConnectionMethod*): Task[Either[BspError, BspSession]]
}

object BspServerConnector {
  sealed abstract class BspConnectionMethod
  final case class UnixLocalBsp(socketFile: File) extends BspConnectionMethod
  final case class WindowsLocalBsp(pipeName: String) extends BspConnectionMethod
  final case class TcpBsp(host: URI, port: Int) extends BspConnectionMethod
}

/** TODO Connects to a bsp server based on information in .bsp directory */
class GenericConnector(base: File, initParams: InitializeBuildParams) extends BspServerConnector(initParams) {

  override def connect(methods: BspConnectionMethod*): Task[Either[BspError, BspSession]] =
    Task.now(Left(BspErrorMessage("unknown bsp servers not supported yet")))
}

