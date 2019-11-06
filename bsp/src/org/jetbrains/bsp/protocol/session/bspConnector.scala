package org.jetbrains.bsp.protocol.session

import java.io.File
import java.net.URI

import ch.epfl.scala.bsp4j.{BspConnectionDetails, BuildClientCapabilities, InitializeBuildParams}
import com.google.gson.{Gson, JsonArray, JsonElement, JsonObject}
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
    val buildClientCapabilities = new BuildClientCapabilities(capabilities.languageIds.asJava)
    val pluginVersion = ScalaPluginVersionVerifier.getPluginVersion.map(_.presentation).getOrElse("N/A")
    val dataJson = new JsonObject()
    dataJson.addProperty("clientClassesRootDir", compilerOutput.toString)
    dataJson.add("supportedScalaVersions", new JsonArray()) // shouldn't need this, but bloop may not parse the data correctly otherwise
    val initializeBuildParams = new InitializeBuildParams("IntelliJ-BSP", pluginVersion, "2.0", rootUri.toString, buildClientCapabilities)
    initializeBuildParams.setData(dataJson)

    initializeBuildParams
  }
}

abstract class BspServerConnector() {
  /**
    * Connect to a bsp server with one of the given methods.
    * @return a BspError if no compatible method is found.
    */
  def connect: Either[BspError, Builder]
}

class DummyConnector(rootUri: URI) extends BspServerConnector() {
  override def connect: Either[BspError, Builder] =
    Left(BspErrorMessage(s"No way found to connect to a BSP server for workspace $rootUri"))
}


