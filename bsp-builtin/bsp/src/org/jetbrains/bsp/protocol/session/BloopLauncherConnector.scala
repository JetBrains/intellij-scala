package org.jetbrains.bsp.protocol.session

import bloop.rifle.{BloopRifleConfig, BloopRifleLogger, BloopThreads, BloopVersion}
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.projectRoots.{JavaSdk, Sdk}
import org.apache.commons.io.input.ClosedInputStream
import org.jetbrains.bsp.buildinfo.BuildInfo
import org.jetbrains.bsp.protocol.session.BspServerConnector.BspCapabilities
import org.jetbrains.bsp.protocol.session.BspSession.Builder
import org.jetbrains.bsp.{BspBundle, BspError}
import org.jetbrains.plugins.scala.DependencyManager
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.build.BuildReporter
import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.{Files, Path}
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

class BloopLauncherConnector(base: Path, compilerOutput: Path, capabilities: BspCapabilities, jdk: Sdk) extends BspServerConnector {

  val bloopVersion: String = BuildInfo.bloopVersion

  override def connect(reporter: BuildReporter): Either[BspError, Builder] = {
    def bloopClasspath(version: String) = {
      val dependencies = Seq(
        ("ch.epfl.scala" % "bloop-frontend_2.12" % version).transitive()
      )

      val launcherClasspath = DependencyManager.resolve(dependencies: _*).map(_.file.toFile)
      Right(launcherClasspath)
    }

    val bloopDataStore = PathManager.getCommonDataPath.resolve("bloop")

    val java = JavaSdk.getInstance().getVMExecutablePath(jdk)
    val retainedBloopVersion = BloopRifleConfig.AtLeast(BloopVersion(bloopVersion))
    val details = BloopRifleConfig.default(
      BloopRifleConfig.Address.DomainSocket(bloopDataStore),
      bloopClasspath,
      workingDir = base.toFile
    ).copy(javaPath = java, retainedBloopVersion = retainedBloopVersion)

    reporter.log(BspBundle.message("bsp.protocol.starting.bloop"))
    val detailsStringRepresentation =
      s"""BloopRifleConfig:
         |  domainSocketPath = $bloopDataStore
         |  workingDir = $base
         |  javaPath = $java
         |  retainedBloopVersion = $retainedBloopVersion
         |""".stripMargin
    reporter.log(BspBundle.message("bsp.protocol.rifle.details", detailsStringRepresentation))

    Right(prepareBspSession(details, bloopDataStore))
  }

  private def prepareBspSession(details: BloopRifleConfig, bloopDataDir: Path): Builder = {

    val threads = BloopThreads.create()
    val (connection, socket, _) = bloop.rifle.BloopServer.bsp(details, base, threads, BloopRifleLogger.nop, 10.seconds, 30.seconds)

    def safeClose(close: => Unit): Unit =
      try {
        close
      } catch {
        case NonFatal(_) => ()
      }

    val cleanup = () => {
      safeClose(connection.stop())
      safeClose(socket.close())
      safeClose(threads.shutdown())
    }
    val pid = Try(Files.readString(bloopDataDir.resolve("pid"))).toOption.flatMap(_.toIntOption).getOrElse(-1)

    val rootUri = base.toCanonicalPath.toUri
    val compilerOutputUri = compilerOutput.toCanonicalPath.toUri
    val initializeBuildParams = BspServerConnector.createInitializeBuildParams(rootUri, compilerOutputUri, capabilities)

    BspSession.builder(pid, socket.getInputStream, ClosedInputStream.INSTANCE, socket.getOutputStream, initializeBuildParams, cleanup)
  }

}
