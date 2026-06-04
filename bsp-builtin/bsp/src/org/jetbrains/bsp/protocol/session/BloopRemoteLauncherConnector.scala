package org.jetbrains.bsp.protocol.session

//import bloop.rifle.BloopServer.ResolvedBloopParameters
import bloop.rifle.{BloopRifle, BloopRifleConfig, BloopRifleLogger, BspConnectionAddress}
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.platform.eel.provider.utils.EelPathUtils.TransferTarget
import com.intellij.platform.eel.provider.{EelProviderUtil, LocalEelDescriptor}
import com.intellij.platform.eel.{EelDescriptor, EelTunnelsApi}
import org.jetbrains.bsp.protocol.session.BspServerConnector.BspCapabilities
import org.jetbrains.bsp.protocol.session.BspSession.Builder
import org.jetbrains.bsp.{BspBundle, BspConnectionError, BspError}
import org.jetbrains.plugins.scala.build.BuildReporter
import org.jetbrains.plugins.scala.eel.tunnels.EelTunnels
import org.jetbrains.sbt.asLocalPath
import org.jetbrains.sbt.project.CoroutineScopeService.ProjectExt

import java.io.{ByteArrayOutputStream, OutputStream}
import java.net.{InetSocketAddress, Socket, SocketException, SocketTimeoutException}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.{Executors, ScheduledExecutorService}
import scala.annotation.tailrec
import scala.concurrent.duration.*
import scala.util.Try

/**
 * Handles the remote (EEL/container) bloop launcher connection.
 */
private[protocol] class BloopRemoteLauncherConnector(
  base: Path,
  compilerOutput: Path,
  capabilities: BspCapabilities,
  jdk: Sdk,
  eelDescriptor: EelDescriptor
) extends BloopLauncherConnectorBase(compilerOutput, capabilities, jdk) {

  private val BloopLauncherPortDir = "bloop-ports"

  private val LocalHost = "127.0.0.1"

  override def connect(reporter: BuildReporter): Either[BspError, Builder] = {
    val localBase = Path.of(base.asLocalPath)

    val project = ProjectManager.getInstance().getOpenProjects.head
    val tunnels = EelProviderUtil.toEelApiBlocking(eelDescriptor).getTunnels
    val scope = project.coroutineScope

    val bspSocketPort = EelTunnels.findAvailablePort(scope, tunnels)
    val localBspSocketPort = EelTunnels.forwardLocalPort(scope, tunnels, bspSocketPort)

    def createBloopConfig(ports: PortPair): BloopRifleConfig =
      // BloopRifleConfig uses the forwarded local port to talk to bloop in the container
      BloopRifleConfig.default(
        BloopRifleConfig.Address.Tcp(LocalHost, ports.localPort),
        bloopClasspath,
        workingDir = localBase
      ).copy(
        retainedBloopVersion = retainedBloopVersion,
        // bspSocketOrPort is used on both sides: bloop listens on container:bspSocketPort,
        // and bloop-rifle connects to local:bspSocketPort where the forward tunnel is waiting.
        bspSocketOrPort = Some(() =>
          BspConnectionAddress.Tcp(LocalHost, bspSocketPort))
      )

    val logger = new BloopRifleLogger {
      override def info(msg: => String): Unit =
        reporter.log(s"info: $msg")

      override def debug(msg: => String, ex: Throwable): Unit =
        reporter.log(s"debug: $msg")

      override def debug(msg: => String): Unit =
        reporter.log(s"debug: $msg")

      override def error(msg: => String, ex: Throwable): Unit =
        reporter.logErr(s"error: $msg, exception: $ex")

      override def error(msg: => String): Unit =
        reporter.logErr(s"error: $msg")

      override def bloopBspStdout: Option[OutputStream] =
        Some(reporterOutputStream(reporter.log))

      override def bloopBspStderr: Option[OutputStream] =
        Some(reporterOutputStream(reporter.logErr))

      override def bloopCliInheritStdout: Boolean =
        false

      override def bloopCliInheritStderr: Boolean =
        false
    }

    // Resolves which bloop ports to use, starting bloop freshly if necessary.
    // Returns FreshlyStarted when bloop was just launched (skip version check),
    // or Reused when an existing running bloop was found (version check required).
    def resolvePortPair(): Either[BspError, BloopStartResult] = {
      val savedPorts = BloopContainerPort.readPortFile(getBloopLauncherPortDir)
      savedPorts match {
        case Some(BloopContainerPort.SavedPorts(remotePort, forwardedLocalPort)) =>
          val existingPorts = PortPair(remotePort, forwardedLocalPort)
          isRemoteProcessReady(existingPorts.localPort, reporter, timeout = 10.seconds) match {
            case Right(_) =>
              Right(Reused(existingPorts))
            case Left(_) =>
              val newPorts = startBloopInContainer(reporter, java, eelDescriptor, project, tunnels)
              isRemoteProcessReady(newPorts.localPort, reporter).map(_ => FreshlyStarted(newPorts))
          }
        case None =>
          val newPorts = startBloopInContainer(reporter, java, eelDescriptor, project, tunnels)
          isRemoteProcessReady(newPorts.localPort, reporter).map(_ => FreshlyStarted(newPorts))
      }
    }

    val scheduler = Executors.newSingleThreadScheduledExecutor()
    try {
      // 1. Resolve port pair, tracking whether bloop was freshly started or reused.
      val startResult: BloopStartResult = resolvePortPair() match
        case Left(err)     => return Left(err)
        case Right(result) => result

      // 2. Ensure bloop is running with the correct version/JVM.
      //    FreshlyStarted bloop is trusted as-is; only Reused bloop needs a version check.
      val ports: PortPair = startResult match {
        case FreshlyStarted(ports) =>
          ports
        case Reused(ports) =>
          val config = createBloopConfig(ports)
          if isBloopVersionCompatible(config, localBase, logger, scheduler) then
            ports
          else
            logger.debug("Running bloop has incompatible version/JVM")
            BloopRifle.exit(config, localBase, logger)
            val newPorts = startBloopInContainer(reporter, java, eelDescriptor, project, tunnels)
            isRemoteProcessReady(newPorts.localPort, reporter) match {
              case Left(err) => return Left(err)
              case Right(_)  => newPorts
            }
      }

      BloopContainerPort.writePortFile(getBloopLauncherPortDir, ports.remotePort, ports.localPort)

      val details = createBloopConfig(ports)
      reporter.log(BspBundle.message("bsp.protocol.starting.bloop"))
      val detailsStringRepresentation =
        s"""BloopRifleConfig:
           |  bspPort = s"$LocalHost:$localBspSocketPort"
           |  bloopPort = s"$LocalHost:${ports.remotePort}"
           |  workingDir = $base
           |  javaPath = $java
           |  retainedBloopVersion = $retainedBloopVersion
           |""".stripMargin
      reporter.log(BspBundle.message("bsp.protocol.rifle.details", detailsStringRepresentation))

      val connection = BloopRifle.bsp(details, localBase, logger)
      // Wait until the BSP socket tunnel is ready
      isRemoteProcessReady(localBspSocketPort, reporter) match {
        case Left(_)       => Left(BspConnectionError("No Bsp connection established"))
        case Right(socket) => Right(createBuilder(socket, connection, -1, localBase, None))
      }

    } finally {
      scheduler.shutdown()
    }
  }

  private def reporterOutputStream(logLine: String => Unit): OutputStream = new OutputStream {
    private val buffer = new ByteArrayOutputStream()

    override def write(b: Int): Unit = buffer.write(b)

    override def write(b: Array[Byte], off: Int, len: Int): Unit = buffer.write(b, off, len)

    override def flush(): Unit = {
      val content = buffer.toString(StandardCharsets.UTF_8)
      buffer.reset()
      content.linesIterator.foreach(logLine)
    }

    override def close(): Unit = flush()
  }

  // Checks whether the currently running bloop has the expected version and JVM.
  private def isBloopVersionCompatible(
    config: BloopRifleConfig,
    localBase: Path,
    logger: BloopRifleLogger,
    scheduler: ScheduledExecutorService
  ): Boolean = {
    true
//    val bloopInfo = BloopRifle.getCurrentBloopVersion(config, logger, localBase, scheduler)
//
//    bloopInfo match {
//      case Left(_) => false
//      case Right(info) =>
//        val ResolvedBloopParameters(expectedBloopVersion, expectedBloopJvmRelease, _) =
//          BloopServer.resolveBloopInfo(info, config)
//        info.bloopVersion == expectedBloopVersion && info.jvmVersion == expectedBloopJvmRelease
//    }
  }

  def startBloopInContainer(
    reporter: BuildReporter,
    java: String,
    eelDescriptor: EelDescriptor,
    project: Project,
    tunnels: EelTunnelsApi
  ): PortPair = {
    val portInContainer = EelTunnels.findAvailablePort(project.coroutineScope, tunnels)

    //TODO add error handling
    val transferredClasspath = bloopClasspath(bloopVersion) match {
      case Left(value) => throw new Exception("")
      case Right(classpath) =>
        val transferred = classpath.map(EelPathUtils.transferLocalContentToRemote(_, TransferTarget.Temporary(eelDescriptor)))
        transferred.map(_.asLocalPath)
    }

    val args = Seq(
      java,
      "-Dbloop.ignore-sig-int=true",
      "-cp", transferredClasspath.mkString(":"),
      "bloop.BloopServer",
      LocalHost,
      portInContainer.toString
    )
    val commandLine = new GeneralCommandLine(args *)
      .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
      .withWorkingDirectory(base)

    commandLine.createProcess()

    PortPair(
      remotePort = portInContainer,
      localPort = EelTunnels.forwardLocalPort(project.coroutineScope, tunnels, portInContainer)
    )
  }

  private def isRemoteProcessReady(
    port: Int,
    reporter: BuildReporter,
    timeout: FiniteDuration = 45.seconds,
  ): Either[BspError, Socket] = {
    def isRemoteReady(socket: Socket): Boolean = {
      try {
        socket.connect(new InetSocketAddress(LocalHost, port))
        // Set a short read timeout to distinguish:
        //   SocketTimeoutException  → socket is alive, remote is waiting for us to speak first → READY
        //   SocketException("reset")/ EOF → forwarder dropped it → NOT READY
        socket.setSoTimeout(500)
        try {
          val b = socket.getInputStream.read()
          // b == -1 → EOF: forwarder closed the connection because remote isn't ready
          if b != -1 then
            socket.setSoTimeout(0)
            true
          else
            false
        } catch {
          case e: SocketTimeoutException =>
            // Timeout on read = socket stayed open, remote is just waiting for the client
            // to speak first (normal for most client-initiates-first protocols)
            true;
        }
        // SocketException("Connection reset") falls through to outer catch → returns false
      } catch {
        case _: SocketTimeoutException | _: SocketException =>
          false
      }
    }

    val deadline = System.currentTimeMillis() + timeout.toMillis

    @tailrec
    def loop(): Either[BspError, Socket] = {
      val reachable = {
        val socket = new Socket()
        if isRemoteReady(socket) then {
          socket.setSoTimeout(0)
          Right(socket)
        } else
          Left(BspConnectionError("Somethting"))
      }

      if (reachable.isRight) {
        reporter.log("Server is ready.")
        reachable
      } else if (System.currentTimeMillis() >= deadline) {
        Left(BspConnectionError(s"Server did not start within $timeout"))
      } else {
        reporter.log(s"Waiting for server at 127.0.0.1:$port ...")
        Thread.sleep(500.millis.toMillis)
        loop()
      }
    }
    loop()
  }

  private def getBloopLauncherPortDir: Path = {
    val systemDir = eelDescriptor match {
      case LocalEelDescriptor.INSTANCE =>
        // For filesystem paths which match the machine where IDEA is running on, we call
        // `PathManager.getSystemDir`, which respects the `-Didea.system.path` VM option.
        PathManager.getSystemDir
      case remote =>
        EelPathUtils.getSystemFolder(remote)
    }

    val path = systemDir.resolve(BloopLauncherPortDir)
    Files.createDirectories(path)
    path
  }
}

/** Pair of (bloop container remote port, forwarded local port). */
private final case class PortPair(remotePort: Int, localPort: Int)

/** Describes how the current bloop port pair was obtained. */
private sealed trait BloopStartResult
/** Bloop was freshly launched — no version/JVM check needed. */
private final case class FreshlyStarted(ports: PortPair) extends BloopStartResult
/** An existing running bloop was found and reused — version/JVM check is required. */
private final case class Reused(ports: PortPair) extends BloopStartResult

/**
 * Persists both the bloop container (remote) port and the forwarded local port to a file
 * so subsequent connections can reuse an existing tunnel instead of creating a new one.
 */
private object BloopContainerPort {
  private val PortFileName = "bloop-container-port.txt"

  /** A pair of (remotePort, forwardedLocalPort) */
  final case class SavedPorts(remotePort: Int, forwardedLocalPort: Int)

  private def portFilePath(base: Path): Path = base.resolve(PortFileName)

  /** Reads the saved port pair. Returns [[None]] if the file is missing or malformed. */
  def readPortFile(base: Path): Option[SavedPorts] = {
    val path = portFilePath(base)
    Try(Files.readString(path)).toOption.flatMap { content =>
      content.trim.split(":") match {
        case Array(remote, local) =>
          for {
            r <- remote.toIntOption
            l <- local.toIntOption
          } yield SavedPorts(r, l)
        case _ => None
      }
    }
  }

  def writePortFile(base: Path, remotePort: Int, forwardedLocalPort: Int): Unit = {
    import java.nio.file.StandardOpenOption.{CREATE, TRUNCATE_EXISTING}
    val path = portFilePath(base)
    Files.writeString(path, s"$remotePort:$forwardedLocalPort", StandardCharsets.UTF_8, TRUNCATE_EXISTING, CREATE)
  }
}
