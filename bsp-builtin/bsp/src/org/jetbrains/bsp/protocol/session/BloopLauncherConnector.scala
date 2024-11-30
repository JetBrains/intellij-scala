package org.jetbrains.bsp.protocol.session

import ch.epfl.scala.bsp4j.BspConnectionDetails
import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.bsp.buildinfo.BuildInfo
import org.jetbrains.bsp.protocol.session.BspServerConnector.BspCapabilities
import org.jetbrains.bsp.protocol.session.BspSession.Builder
import org.jetbrains.bsp.{BspBundle, BspError}
import org.jetbrains.plugins.scala.DependencyManager
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.build.BuildReporter

import java.io.File
import scala.jdk.CollectionConverters._

class BloopLauncherConnector(base: File, compilerOutput: File, capabilities: BspCapabilities, jdk: Sdk) extends BspServerConnector {

  val bloopVersion: String = BuildInfo.bloopVersion
  val bspVersion = "2.0.0"

  override def connect(reporter: BuildReporter): Either[BspError, Builder] = {

    val dependencies = Seq(
      ("ch.epfl.scala" % "bloop-launcher_2.12" % bloopVersion).transitive()
    )
    val launcherClasspath = DependencyManager.resolve(dependencies: _*)
      .map(_.file.getCanonicalPath)
      .asJava

    val javaParameters: JavaParameters = new JavaParameters
    javaParameters.setJdk(jdk)
    javaParameters.setWorkingDirectory(base)
    javaParameters.getClassPath.addAll(launcherClasspath)
    javaParameters.setMainClass("bloop.launcher.Launcher")

    val cmdLine = javaParameters.toCommandLine
    cmdLine.addParameter(bloopVersion)

    val argv = cmdLine.getCommandLineList(null)

    reporter.log(BspBundle.message("bsp.protocol.starting.bloop"))
    //noinspection ReferencePassedToNls
    reporter.log(cmdLine.getCommandLineString)

    val details = new BspConnectionDetails("Bloop", argv, bloopVersion, bspVersion, List("java","scala").asJava)
    Right(prepareBspSession(details))
  }

  private def prepareBspSession(details: BspConnectionDetails): Builder = {

    val processBuilder = new java.lang.ProcessBuilder(details.getArgv).directory(base)
    val process = processBuilder.start()

    val cleanup = () => {
      process.destroy()
    }

    val rootUri = base.getCanonicalFile.toURI
    val compilerOutputUri = compilerOutput.getCanonicalFile.toURI
    val initializeBuildParams = BspServerConnector.createInitializeBuildParams(rootUri, compilerOutputUri, capabilities)

    BspSession.builder(process.pid(), process.getInputStream, process.getErrorStream, process.getOutputStream, initializeBuildParams, cleanup)
  }

}
