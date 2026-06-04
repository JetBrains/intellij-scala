package org.jetbrains.bsp.protocol.session

import bloop.rifle.{BloopRifleConfig, BloopThreads, BloopVersion, BspConnection}
import com.intellij.openapi.projectRoots.{JavaSdk, Sdk}
import org.apache.commons.io.input.ClosedInputStream
import org.jetbrains.bsp.buildinfo.BuildInfo
import org.jetbrains.bsp.protocol.session.BspServerConnector.BspCapabilities
import org.jetbrains.bsp.protocol.session.BspSession.Builder
import org.jetbrains.plugins.scala.DependencyManager
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.sbt.asLocalPath

import java.net.Socket
import java.nio.file.Path
import scala.util.control.NonFatal

/**
 * Abstract base for bloop launcher connectors.
 * Holds all shared constructor parameters and provides shared utility methods
 */
private[protocol] abstract class BloopLauncherConnectorBase(
  val compilerOutput: Path,
  val capabilities: BspCapabilities,
  val jdk: Sdk
) extends BspServerConnector {

  protected val bloopVersion: String = BuildInfo.bloopVersion

  protected val retainedBloopVersion =
    BloopRifleConfig.AtLeast(BloopVersion(bloopVersion))

  protected lazy val java: String =
    JavaSdk.getInstance().getVMExecutablePath(jdk)

  protected def bloopClasspath(version: String): Either[Throwable, Seq[Path]] = {
    val dependencies = Seq(
      ("ch.epfl.scala" % "bloop-frontend_2.12" % version).transitive()
    )
    Right(DependencyManager.resolve(dependencies *).map(_.file))
  }

  protected def createBuilder(socket: Socket, connection: BspConnection, pid: Int, localBase: Path, threads: Option[BloopThreads]): Builder = {
    def safeClose(close: => Unit): Unit =
      try {
        close
      } catch {
        case NonFatal(_) => ()
      }

    val cleanup = () => {
      safeClose(connection.stop())
      safeClose(socket.close())
      safeClose(threads.foreach(_.shutdown()))
    }

    val rootUri = localBase.toUri
    val compilerOutputUri = Path.of(compilerOutput.asLocalPath).toUri
    val initializeBuildParams = BspServerConnector.createInitializeBuildParams(rootUri, compilerOutputUri, capabilities)

    BspSession.builder(pid, socket.getInputStream, ClosedInputStream.INSTANCE, socket.getOutputStream, initializeBuildParams, cleanup)
  }
}
