package org.jetbrains.bsp.protocol.session

import bloop.rifle.{BloopRifleConfig, BloopThreads, BloopVersion, BspConnection}
import com.intellij.openapi.projectRoots.{JavaSdk, Sdk}
import com.intellij.platform.eel.provider.utils.EelPathUtils
import org.apache.commons.io.input.ClosedInputStream
import org.jetbrains.bsp.buildinfo.BuildInfo
import org.jetbrains.bsp.protocol.session.BspServerConnector.BspCapabilities
import org.jetbrains.bsp.protocol.session.BspSession.Builder
import org.jetbrains.plugins.scala.DependencyManager
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.extensions.PathExt

import java.net.Socket
import java.nio.file.Path
import kotlinx.coroutines.{CoroutineScope, CoroutineScopeKt}
import scala.util.control.NonFatal

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

  protected def createBuilder(socket: Socket, connection: BspConnection, pid: Long, base: Path, threads: Option[BloopThreads], scope: Option[CoroutineScope]): Builder = {
    def safeClose(close: => Unit): Unit =
      try {
        close
      } catch {
        case NonFatal(_) => ()
      }

    val cleanup = () => {
      scope.foreach(CoroutineScopeKt.cancel(_, null))
      safeClose(connection.stop())
      safeClose(socket.close())
      safeClose(threads.foreach(_.shutdown()))
    }

    val rootUri = EelPathUtils.getUriLocalToEel(base.toCanonicalPath)
    val compilerOutputUri = EelPathUtils.getUriLocalToEel(compilerOutput.toCanonicalPath)
    val initializeBuildParams = BspServerConnector.createInitializeBuildParams(rootUri, compilerOutputUri, capabilities)

    BspSession.builder(pid, socket.getInputStream, ClosedInputStream.INSTANCE, socket.getOutputStream, initializeBuildParams, cleanup)
  }
}
