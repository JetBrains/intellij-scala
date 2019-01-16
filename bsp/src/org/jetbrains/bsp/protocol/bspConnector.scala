package org.jetbrains.bsp.protocol

import java.io.File
import java.net.URI

import ch.epfl.scala.bsp4j.{BspConnectionDetails, BuildClientCapabilities, InitializeBuildParams}
import org.jetbrains.bsp.protocol.BspServerConnector.{BspCapabilities, BspConnectionMethod, ProcessBsp}
import org.jetbrains.bsp.protocol.BspSession.BspSessionBuilder
import org.jetbrains.bsp.{BspError, BspErrorMessage}
import org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier

import scala.collection.JavaConverters._


object BspServerConnector {
  sealed abstract class BspConnectionMethod
  final case class ProcessBsp(details: BspConnectionDetails) extends BspConnectionMethod
  final case class UnixLocalBsp(socketFile: File) extends BspConnectionMethod
  final case class WindowsLocalBsp(pipeName: String) extends BspConnectionMethod
  final case class TcpBsp(host: URI, port: Int) extends BspConnectionMethod

  case class BspCapabilities(languageIds: List[String])
}

abstract class BspServerConnectorSync(val rootUri: URI, val capabilities: BspCapabilities) {
  /**
    * Connect to a bsp server with one of the given methods.
    * @param methods methods supported by the bsp server, in order of preference
    * @return None if no compatible method is found. TODO should be an error response
    */
  def connect(methods: BspConnectionMethod*): Either[BspError, BspSessionBuilder]
}

class DummyConnector(rootUri: URI) extends BspServerConnectorSync(rootUri, BspCapabilities(Nil)) {
  override def connect(methods: BspConnectionMethod*): Either[BspError, BspSessionBuilder] =
    Left(BspErrorMessage(s"No way found to connect to a BSP server for workspace $rootUri"))
}

/** TODO Connects to a bsp server based on information in a bsp configuration directory. */
class GenericConnectorSync(base: File, capabilities: BspCapabilities) extends BspServerConnectorSync(base.getCanonicalFile.toURI, capabilities) {

  override def connect(methods: BspConnectionMethod*): Either[BspError, BspSessionBuilder] = {
    methods.collectFirst {
      case ProcessBsp(details: BspConnectionDetails) =>
        // TODO check bsp version compatibility
        // TODO check languages compatibility
        Right(prepareBspSession(details))
    }.getOrElse(Left(BspErrorMessage("no supported connection method for this server")))
  }

  private def prepareBspSession(details: BspConnectionDetails): BspSessionBuilder = {
    details.getArgv

    val process =
      new java.lang.ProcessBuilder(details.getArgv)
        .directory(base)
        .start()

    val cleanup = () => {
      process.destroy()
    }

    val buildClientCapabilities = new BuildClientCapabilities(capabilities.languageIds.asJava)
    val pluginVersion = ScalaPluginVersionVerifier.getPluginVersion.map(_.presentation).getOrElse("N/A")
    val initializeBuildParams = new InitializeBuildParams("IntelliJ-BSP", pluginVersion, "2.0", rootUri.toString, buildClientCapabilities)

    BspSession.builder(process.getInputStream, process.getErrorStream, process.getOutputStream, initializeBuildParams, cleanup)
  }
}
