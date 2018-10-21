package org.jetbrains.bsp.protocol

import java.io.File
import java.net.URI

import org.jetbrains.bsp.protocol.BspServerConnector.{BspCapabilities, BspConnectionMethod}
import org.jetbrains.bsp.{BspError, BspErrorMessage}


object BspServerConnector {
  sealed abstract class BspConnectionMethod
  final case class UnixLocalBsp(socketFile: File) extends BspConnectionMethod
  final case class WindowsLocalBsp(pipeName: String) extends BspConnectionMethod
  final case class TcpBsp(host: URI, port: Int) extends BspConnectionMethod

  case class BspCapabilities(languageIds: List[String], providesFileWatching: Boolean)
}

abstract class BspServerConnectorSync(val rootUri: URI, val capabilities: BspCapabilities) {
  /**
    * Connect to a bsp server with one of the given methods.
    * @param methods methods supported by the bsp server, in order of preference
    * @return None if no compatible method is found. TODO should be an error response
    */
  def connect(methods: BspConnectionMethod*): Either[BspError, Bsp4jSession]
}

/** TODO Connects to a bsp server based on information in a bsp configuration directory. */
class GenericConnectorSync(base: File, capabilities: BspCapabilities) extends BspServerConnectorSync(base.getCanonicalFile.toURI, capabilities) {

  override def connect(methods: BspConnectionMethod*): Either[BspError, Bsp4jSession] =
    Left(BspErrorMessage("unknown bsp servers not supported yet"))
}
