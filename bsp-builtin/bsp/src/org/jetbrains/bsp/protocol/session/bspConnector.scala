package org.jetbrains.bsp.protocol.session

import ch.epfl.scala.bsp4j.{BspConnectionDetails, BuildClientCapabilities, InitializeBuildParams}
import com.google.gson.{JsonArray, JsonObject}
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.bsp.BspError
import org.jetbrains.bsp.protocol.session.BspSession.Builder
import org.jetbrains.plugins.scala.build.BuildReporter
import org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier

import java.net.URI
import scala.jdk.CollectionConverters.*

object BspServerConnector {
  sealed abstract class BspConnectionMethod
  final case class ProcessBsp(details: BspConnectionDetails) extends BspConnectionMethod

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

abstract class BspServerConnector {
  /**
    * Connect to a bsp server with one of the given methods.
    * @return a BspError if no compatible method is found.
    */
  def connect(using reporter: BuildReporter, indicator: Option[ProgressIndicator]): Either[BspError, Builder]
}

