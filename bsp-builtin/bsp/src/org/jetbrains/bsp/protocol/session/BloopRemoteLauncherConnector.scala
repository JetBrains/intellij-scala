package org.jetbrains.bsp.protocol.session

import bloop.rifle.{BloopRifle, BloopRifleConfig, BloopRifleLogger, BloopVersion, BspConnectionAddress}
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.progress.CoroutinesKt.runBlockingCancellable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.fs.EelFiles
import com.intellij.platform.eel.provider.EelProviderUtil
import com.intellij.platform.eel.provider.utils.EelPathUtils.TransferTarget
import com.intellij.platform.eel.provider.utils.{EelPathUtils, IjentTunnelsUtil}
import com.intellij.platform.util.coroutines.CoroutineScopeKt.childScope
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.bsp.protocol.session.BloopRemoteLauncherConnector.{readBloopRemotePort, writeBloopRemotePort}
import org.jetbrains.bsp.protocol.session.BspServerConnector.BspCapabilities
import org.jetbrains.bsp.protocol.session.BspSession.Builder
import org.jetbrains.bsp.{BspBundle, BspError, BspSessionCreationError, BspTaskCancelled}
import org.jetbrains.plugins.scala.build.BuildReporter
import org.jetbrains.plugins.scala.eel.tunnels.EelTunnels
import org.jetbrains.plugins.scala.util.PathUtil
import org.jetbrains.sbt.asLocalPath
import org.jetbrains.sbt.project.CoroutineAppScopeService

import java.net.{InetSocketAddress, Socket, SocketTimeoutException}
import java.nio.file.StandardOpenOption.{CREATE, TRUNCATE_EXISTING}
import java.nio.file.{Files, Path}
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.{CoroutineScope, CoroutineScopeKt}
import scala.annotation.tailrec
import scala.concurrent.duration.*
import scala.util.Try
import scala.util.control.NonFatal

/**
 * Manages a BSP connection to a Bloop server running on a remote machine (Docker/WSL).
 * This includes starting the Bloop server on the remote machine and using the Bloop Rifle library to create a BSP connection.
 *
 * **Overview**<br>
 * The Bloop server is started inside the remote environment (Docker/WSL) and listens on a TCP port there.
 * Since the Bloop Rifle library operates on the host machine, EEL port forwarding is used - the remote Bloop port
 * is forwarded to a local port so that Rifle can communicate with the server as if it were local.
 * Similarly, the BSP socket port opened by Bloop on the remote side is forwarded locally for the IDE to consume.
 *
 * **Port forwarding lifecycle**<br>
 * All port forwards (both for the Bloop server and the BSP socket) are scoped to a shared coroutine scope.
 * If the BSP connection fails or the BSP socket is closed, the entire scope is canceled, which tears down
 * all active port forwards. On the next connection attempt, fresh forwarding is established.
 * This avoids accumulating unused port forwards.
 *
 * **Bloop port persistence**<br>
 * The remote Bloop server port is persisted to a file on the host ([[BloopRemoteLauncherConnector.PortFileName]]).
 * On later connections, the saved port is read and checked to determine whether the existing Bloop instance can be reused.
 */
private[protocol] class BloopRemoteLauncherConnector(
  base: Path,
  compilerOutput: Path,
  capabilities: BspCapabilities,
  jdk: Sdk,
  eelDescriptor: EelDescriptor
) extends BloopLauncherConnectorBase(compilerOutput, capabilities, jdk) {

  private val BloopLauncherPortDirName = "bloop-ports"
  private val LocalHost = "127.0.0.1"

  private val localBasePath = Path.of(base.asLocalPath)

  private lazy val bloopLauncherPortDir = {
    val systemDir = PathUtil.getSystemDirectory(eelDescriptor)
    val bloopLauncherPortDir = systemDir.resolve(BloopLauncherPortDirName)
    Files.createDirectories(bloopLauncherPortDir)
    bloopLauncherPortDir
  }

  private lazy val eelTunnels = EelProviderUtil.toEelApiBlocking(eelDescriptor).getTunnels

  override def connect(using reporter: BuildReporter, indicator: Option[ProgressIndicator]): Either[BspError, Builder] = {
    val appCoroutineScope = CoroutineAppScopeService.coroutineScope
    given scope: CoroutineScope = childScope(appCoroutineScope, "Bloop port forwarding", EmptyCoroutineContext.INSTANCE, true)
    try {
      val bspSocketRemotePort = findAvailablePort()
      val result = for
        bloopPorts <- resolveBloopPorts(bspSocketRemotePort)
        builder    <- establishBspConnection(bloopPorts, bspSocketRemotePort)
      yield builder

      result.left.foreach(_ => CoroutineScopeKt.cancel(scope, null))
      result
    } catch {
      case NonFatal(e) =>
        CoroutineScopeKt.cancel(scope, null)
        throw e
    }
  }

  /** Pair of Bloop ports - the remote port running on a remote host and forwarded local port. */
  private case class BloopPorts(remote: Int, local: Int)

  private def resolveBloopPorts(bspSocketRemotePort: Int)(using reporter: BuildReporter, scope: CoroutineScope, indicator: Option[ProgressIndicator]): Either[BspError, BloopPorts] = {
    val bloopRemotePort = readBloopRemotePort(bloopLauncherPortDir)
    val resolvedPorts = bloopRemotePort match {
      case Some(port) =>
        val forwardLocalBloop = EelTunnels.forwardLocalPort(scope, eelTunnels, port)
        val bloopPorts = BloopPorts(port, forwardLocalBloop)
        checkAndReuseOrRestartBloop(bloopPorts, bspSocketRemotePort)
      case None =>
        startBloop
    }

    resolvedPorts.flatMap { ports =>
      writeBloopRemotePort(bloopLauncherPortDir, ports.remote) match
        case Right(_) => Right(ports)
        case Left(err) =>
          exitRunningBloop(config = createBloopConfig(ports, bspSocketRemotePort))
          Left(err)
    }
  }

  private def exitRunningBloop(config: BloopRifleConfig): Unit =
    BloopRifle.exit(config, localBasePath, BloopRifleLogger.nop)

  /**
   * Checks whether Bloop is running by sending the `about` command to the server
   * and retrieving server information such as the Bloop version and JVM.
   * If the running Bloop server versions do not match the expected values, the existing server is stopped and a new one is started.
   *
   * It mirrors the form the Bloop Rifle library: `bloop.rifle.BloopServer.ensureBloopRunning`
   * [[https://github.com/scalacenter/bloop/blob/14938783462483b23845e1b4c6ffaa23a247197d/bloop-rifle/src/main/scala/bloop/rifle/BloopServer.scala#L65]]
   */
  private def checkAndReuseOrRestartBloop(
    bloopPorts: BloopPorts,
    bspSocketRemotePort: Int
  )(using reporter: BuildReporter, scope: CoroutineScope, indicator: Option[ProgressIndicator]): Either[BspError, BloopPorts] = {
    val config = createBloopConfig(bloopPorts, bspSocketRemotePort)
    val bloopServerInfo = BloopRifle.getCurrentBloopVersion(
      config, BloopRifleLogger.nop, localBasePath, AppExecutorUtil.getAppScheduledExecutorService
    )
    bloopServerInfo match {
      case Left(_) =>
        startBloop
      case Right(info) =>
        val (expectedBloopVersion, expectedBloopJvmRelease) = resolveBloopInfo(info, config)
        val isUpToDate = info.bloopVersion == expectedBloopVersion && info.jvmVersion == expectedBloopJvmRelease
        if (isUpToDate) {
          reporter.log(BspBundle.message("bsp.protocol.bloop.remote.reusing.server", bloopPorts.remote.toString))
          Right(bloopPorts)
        } else {
          reporter.log(BspBundle.message("bsp.protocol.bloop.remote.exiting.outdated.server", bloopPorts.remote.toString))
          exitRunningBloop(config)
          startBloop
        }
    }
  }

  private def establishBspConnection(
    bloopPorts: BloopPorts,
    bspSocketRemotePort: Int
  )(using reporter: BuildReporter, scope: CoroutineScope, indicator: Option[ProgressIndicator]): Either[BspError, Builder] = {
    val bspSocketLocalPort = EelTunnels.forwardLocalPort(scope, eelTunnels, bspSocketRemotePort)

    reporter.log(BspBundle.message("bsp.protocol.bloop.remote.starting.bsp.connection", bspSocketRemotePort.toString))

    val config = createBloopConfig(bloopPorts, bspSocketRemotePort)
    val configRepresentation =
      s"""BloopRifleConfig:
         |  bspPort = $LocalHost:$bspSocketRemotePort
         |  bloopPort = $LocalHost:${bloopPorts.remote}
         |  workingDir = $base
         |  javaPath = $java
         |  retainedBloopVersion = $retainedBloopVersion
         |""".stripMargin
    reporter.log(BspBundle.message("bsp.protocol.rifle.details", configRepresentation))

    val connection = BloopRifle.bsp(config, localBasePath, BloopRifleLogger.nop)

    awaitRemoteProcess(bspSocketLocalPort, "BSP socket").map: socket =>
      createBuilder(socket, connection, GenericConnector.RemoteProcessPid, base, threads = None, Some(scope))
  }

  /**
   * Creates the configuration used by the Bloop Rifle library.
   * The Bloop address uses the local forwarded port because the Bloop Rifle library runs on the local machine.
   * The BSP socket uses the remote port because this is the port where the remote Bloop server opens the socket.
   */
  private def createBloopConfig(bloopPorts: BloopPorts, bspSocketRemotePort: Int): BloopRifleConfig =
    BloopRifleConfig.default(
      BloopRifleConfig.Address.Tcp(LocalHost, bloopPorts.local),
      bloopClasspath,
      workingDir = localBasePath
    ).copy(
      retainedBloopVersion = retainedBloopVersion,
      bspSocketOrPort = Some(() => BspConnectionAddress.Tcp(LocalHost, bspSocketRemotePort))
    )

  // Copied from Bloop library; it's private there (bloop.rifle.BloopServer.resolveBloopInfo)
  private def resolveBloopInfo(
    bloopInfo: BloopRifle.BloopServerRuntimeInfo,
    config: BloopRifleConfig
  ): (BloopVersion, Int) = {
    given Ordering[BloopVersion] = Ordering.fromLessThan[BloopVersion](_ isOlderThan _)
    val bloopV = Seq(bloopInfo.bloopVersion, retainedBloopVersion.version).max
    val jvmV = List(bloopInfo.jvmVersion, config.minimumBloopJvm).max
    (bloopV, jvmV)
  }

  private def startBloop(using reporter: BuildReporter, scope: CoroutineScope, indicator: Option[ProgressIndicator]): Either[BspError, BloopPorts] = {
    val classpath = transferBloopClasspath.fold(
      err => return Left(err),
      identity
    )
    val bloopRemotePort = findAvailablePort()

    reporter.log(BspBundle.message("bsp.protocol.bloop.remote.starting.server", bloopRemotePort.toString))
    // the command to start Bloop is copied from Bloop Rifle library: bloop.rifle.internal.Operations.startServer
    val args = Seq(
      java,
      "-Dbloop.ignore-sig-int=true",
      "-cp", classpath.mkString(":"),
      "bloop.BloopServer",
      LocalHost,
      bloopRemotePort.toString
    )

    val commandLine = new GeneralCommandLine(args *)
      .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
      .withWorkingDirectory(base)

    Try(commandLine.createProcess()).toEither
      .left.map(exc => BspSessionCreationError(BspBundle.message("bsp.protocol.bloop.remote.cannot.start.server"), exc))
      .flatMap { _ =>
        val bloopLocalPort = EelTunnels.forwardLocalPort(scope, eelTunnels, bloopRemotePort)
        awaitRemoteProcess(bloopLocalPort, "Bloop server").map: socket =>
          socket.close()
          reporter.log(BspBundle.message("bsp.protocol.bloop.remote.server.listening", bloopRemotePort.toString))
          BloopPorts(bloopRemotePort, bloopLocalPort)
      }
  }

  /** Resolves and transfers the Bloop classpath to the remote machine. */
  private def transferBloopClasspath: Either[BspError, Seq[String]] =
    bloopClasspath(bloopVersion)
      .left.map(err => BspSessionCreationError(BspBundle.message("bsp.protocol.bloop.cannot.download.classpath"), err))
      .map { classpath =>
        // It can take like 5-10 seconds to transfer, depending on the machine.
        // It might be worth checking if it is possible to download it directly inside the remote machine instead of transferring.
        classpath.map: path =>
          EelPathUtils.transferLocalContentToRemote(path, TransferTarget.Temporary(eelDescriptor)).asLocalPath
      }

  /** Finds a free port on a remote machine to start the Bloop server or open a BSP socket connection. */
  // There is a very small chance that the port returned by this method will no longer
  // be available when Bloop tries to use it. For now, let's see how this works in practice and fix it in the future if needed.
  private def findAvailablePort(): Integer =
    runBlockingCancellable { (_, continuation) =>
      IjentTunnelsUtil.findAvailablePort(eelTunnels, continuation)
    }

  /**
   * Waits for a remote process to become ready and returns a connected socket to the locally forwarded port.
   *
   * When working with remote processes that use local port forwarding, simply creating
   * a socket to the local port is not enough to verify that the remote process is ready.
   * The local port is listening since forwarding, so the connection succeeds immediately, even if the remote process has not started yet.
   *
   * This method connects to the local port and attempts to read a byte with a short timeout.
   * The connection behavior reveals whether the remote process is ready:
   * - `SocketTimeoutException`: Waiting for a client to speak first → READY
   * - Successful read → READY
   * - Any other exception → NOT READY
   *
   * @param localPort the local forwarded port to connect to
   * @param description readable description of the process `Bloop server`/`BSP socket`
   */
  private def awaitRemoteProcess(
    localPort: Int,
    description: String
  )(using reporter: BuildReporter, indicator: Option[ProgressIndicator]): Either[BspError, Socket] = {
    def isReady(socket: Socket): Boolean =
      try {
        val connectTimeoutMs = 30000
        socket.connect(InetSocketAddress(LocalHost, localPort), connectTimeoutMs)
        socket.setSoTimeout(500) // Set short read timeout to not block indefinitely
        val byte = socket.getInputStream.read()
        byte != -1 // -1 = EOF means remote closed connection
      } catch {
        case _: SocketTimeoutException => true // Timeout = remote is waiting
        case _: Exception => false
      }

    val timeout = 35.seconds
    val deadline = System.nanoTime() + timeout.toNanos

    @tailrec
    def pollUntilReady(): Either[BspError, Socket] =
      if indicator.exists(_.isCanceled) then
        Left(BspTaskCancelled)
      else if System.nanoTime() >= deadline then
        val msg = BspBundle.message("bsp.protocol.bloop.remote.not.ready", description, timeout.toString)
        Left(BspSessionCreationError(msg, Exception(msg)))
      else
        val socket = new Socket()
        if isReady(socket) then
          socket.setSoTimeout(0) // Clear timeout for normal use
          reporter.log(BspBundle.message("bsp.protocol.bloop.remote.ready", description))
          Right(socket)
        else
          socket.close()
          reporter.log(BspBundle.message("bsp.protocol.bloop.remote.waiting.for", description))
          Thread.sleep(500)
          pollUntilReady()

    pollUntilReady()
  }
}

private[protocol] object BloopRemoteLauncherConnector {
  /** File name used to persist the remote Bloop server between IDE sessions. */
  private val PortFileName = "bloop-container-port.txt"

  private def portFile(base: Path): Path = base.resolve(PortFileName)

  /** Reads the saved bloop port running on a remote machine. */
  def readBloopRemotePort(base: Path): Option[Int] =
    Try(EelFiles.readString(portFile(base)))
      .toOption
      .flatMap(_.toIntOption)

  /** Writes the bloop server port running on a remote machine. */
  def writeBloopRemotePort(base: Path, port: Int): Either[BspError, Unit] =
    Try(Files.writeString(portFile(base), port.toString, TRUNCATE_EXISTING, CREATE)).toEither
      .left.map(exc => BspSessionCreationError(BspBundle.message("bsp.protocol.bloop.remote.cannot.write.port.file", portFile(base).toString), exc))
      .map(_ => ())
}
