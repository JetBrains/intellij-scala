package org.jetbrains.bsp.protocol.session

import java.io.File

import ch.epfl.scala.bsp4j.{BspConnectionDetails, BuildClientCapabilities, InitializeBuildParams}
import org.jetbrains.bsp.protocol.session.BspServerConnector.{BspCapabilities, BspConnectionMethod, ProcessBsp}
import org.jetbrains.bsp.protocol.session.BspSession.Builder
import org.jetbrains.bsp.{BspError, BspErrorMessage}
import org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier
import scala.collection.JavaConverters._

class GenericConnector(base: File, capabilities: BspCapabilities) extends BspServerConnector(base.getCanonicalFile.toURI, capabilities) {

  override def connect(methods: BspConnectionMethod*): Either[BspError, Builder] = {
    methods.collectFirst {
      case ProcessBsp(details: BspConnectionDetails) =>
        // TODO check bsp version compatibility
        // TODO check languages compatibility
        Right(prepareBspSession(details))
    }.getOrElse(Left(BspErrorMessage("no supported connection method for this server")))
  }

  private def prepareBspSession(details: BspConnectionDetails): Builder = {
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
