package org.jetbrains.bsp.protocol.session

import java.io.File
import java.net.URI

import ch.epfl.scala.bsp4j.{BspConnectionDetails, BuildClientCapabilities, InitializeBuildParams}
import com.google.gson.{Gson, JsonElement, JsonObject}
import org.jetbrains.bsp.protocol.session.BspServerConnector.{BspCapabilities, BspConnectionMethod, ProcessBsp}
import org.jetbrains.bsp.protocol.session.BspSession.Builder
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

  private[session] def createInitializeBuildParams(rootUri: URI, compilerOutput: URI, capabilities: BspCapabilities) = {
    val gson: Gson = new Gson()

    val buildClientCapabilities = new BuildClientCapabilities(capabilities.languageIds.asJava)
    val pluginVersion = ScalaPluginVersionVerifier.getPluginVersion.map(_.presentation).getOrElse("N/A")
    val dataJson = new JsonObject()
    dataJson.addProperty("clientClassesRootDir", compilerOutput.toString)
    val data = gson.toJson(dataJson)
    val initializeBuildParams = new InitializeBuildParams("IntelliJ-BSP", pluginVersion, "2.0", rootUri.toString, buildClientCapabilities)
    initializeBuildParams.setData(data)

    initializeBuildParams
  }
}

abstract class BspServerConnector() {
  /**
    * Connect to a bsp server with one of the given methods.
    * @param methods methods supported by the bsp server, in order of preference
    * @return None if no compatible method is found. TODO should be an error response
    */
  def connect(methods: BspConnectionMethod*): Either[BspError, Builder]
}

class DummyConnector(rootUri: URI) extends BspServerConnector() {
  override def connect(methods: BspConnectionMethod*): Either[BspError, Builder] =
    Left(BspErrorMessage(s"No way found to connect to a BSP server for workspace $rootUri"))
}


