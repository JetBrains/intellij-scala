package org.jetbrains.jps.incremental.scala.remote

import java.io._
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}
import java.util.{Base64, Timer, TimerTask}

import com.martiansoftware.nailgun.NGContext
import org.jetbrains.jps.api.{BuildType, CmdlineProtoUtil}
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.cmdline.{BuildRunner, JpsModelLoaderImpl}
import org.jetbrains.jps.incremental.{MessageHandler, Utils}
import org.jetbrains.jps.incremental.fs.BuildFSState
import org.jetbrains.jps.incremental.messages.{BuildMessage, CustomBuilderMessage, ProgressMessage}
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.data.CompileServerCommandParser
import org.jetbrains.jps.incremental.scala.local.LocalServer
import org.jetbrains.jps.incremental.scala.local.worksheet.WorksheetServer
import org.jetbrains.jps.incremental.scala.remote.MeteringScheduler.ArgsParsed
import org.jetbrains.plugins.scala.compiler.CompilerEvent
import org.jetbrains.plugins.scala.compiler.data.Arguments
import org.jetbrains.plugins.scala.compiler.data.serialization.SerializationUtils
import org.jetbrains.plugins.scala.compiler.data.worksheet.WorksheetArgs
import org.jetbrains.plugins.scala.server.CompileServerToken

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

/**
 * Nailgun Nail, used in:
 *
 * @see [[org.jetbrains.plugins.scala.nailgun.NailgunRunner]]<br>
 *      [[org.jetbrains.plugins.scala.nailgun.NailgunMainLightRunner]]
 *      [[org.jetbrains.plugins.scala.compiler.NonServerRunner]]
 */
object Main {
  private val server = new LocalServer()
  private val worksheetServer = new WorksheetServer

  private var shutdownTimer: Timer = _

  private val maxHeapSize = Runtime.getRuntime.maxMemory()
  private val currentParallelism = new AtomicInteger(0)

  private var buildSystemDir: Path = _

  def setup(buildSystemDir: Path): Unit = {
    this.buildSystemDir = buildSystemDir
    Utils.setSystemRoot(buildSystemDir.toFile)
  }

  def getCurrentParallelism(): Int = currentParallelism.get()

  /**
   * This method is called by NGServer
   *
   * @see [[http://www.martiansoftware.com/nailgun/quickstart.html]]<br>
   *      [[http://www.martiansoftware.com/nailgun/doc/javadoc/com/martiansoftware/nailgun/NGContext.html]]<br>
   *      [[com.martiansoftware.nailgun.NGContext]]<br>
   *      [[com.martiansoftware.nailgun.NGSession:153]]<br>
   *      [[com.martiansoftware.nailgun.NGServer:198]]<br>
   */
  def nailMain(context: NGContext): Unit = {
    cancelShutdownTimer()
    serverLogic(
      commandId = context.getCommand,
      argsEncoded = context.getArgs.toSeq,
      out = context.out,
      port = context.getNGServer.getPort,
      standalone = false
    )
    resetShutdownTimer(context)
  }

  def main(args: Array[String]): Unit = {
    serverLogic(CommandIds.Compile, args.toIndexedSeq, System.out, -1, standalone = true)
  }

  private def serverLogic(commandId: String,
                          argsEncoded: Seq[String],
                          out: PrintStream,
                          port: Int,
                          standalone: Boolean): Unit = {
    if (buildSystemDir == null)
      throw new IllegalStateException("the 'setup' method must be invoked before compile server usage")
    val client = new EncodingEventGeneratingClient(out, standalone)
    val oldOut = System.out
    // Suppress any stdout data, interpret such data as error
    System.setOut(System.err)


    try {
      val argsParsed = parseArgs(commandId, argsEncoded) match {
        case Success(result) =>
          result
        case Failure(error) =>
          client.trace(error)
          return
      }

      // Don't check token in non-server mode
      if (port != -1) {
        try {
          val tokenPath = CompileServerToken.tokenPathForPort(buildSystemDir, port)
          validateToken(tokenPath, argsParsed.token)
        } catch {
          // We must abort the process on _any_ error
          case e: Throwable =>
            client.error(e.getMessage)
            return
        }
      }

      handleCommand(argsParsed.command, client)
    } catch {
      case e: Throwable =>
        client.trace(e)
    } finally {
      client.processingEnd()
      client.close()
      System.setOut(oldOut)
    }
  }

  private def handleCommand(command: CompileServerCommand, client: EncodingEventGeneratingClient): Unit = {
    def decorated(action: => Unit): Unit =
      if (command.isCompileCommand)
        try {
          currentParallelism.incrementAndGet()
          action
        } finally {
          currentParallelism.decrementAndGet()
        }
      else
        action

    decorated {
      command match {
        case CompileServerCommand.Compile(arguments) =>
          compileLogic(arguments, client)
        case compileJps: CompileServerCommand.CompileJps =>
          compileJpsLogic(compileJps, client)
        case getMetrics: CompileServerCommand.GetMetrics =>
          getMetricsLogic(getMetrics, client)
        case startMetering: CompileServerCommand.StartMetering =>
          startMeteringLogic(startMetering, client)
        case endMetering: CompileServerCommand.EndMetering =>
          endMeteringLogic(endMetering, client)
      }
    }
  }

  private def compileLogic(args: Arguments, client: EncodingEventGeneratingClient): Unit = {
    val worksheetArgs = args.worksheetArgs
    val needToCompile = !worksheetArgs.exists(_.isInstanceOf[WorksheetArgs.RunRepl])
    if (needToCompile) {
      server.compile(args.sbtData, args.compilerData, args.compilationData, client)
    }

    worksheetArgs match {
      case Some(wa) if !client.hasErrors =>
        worksheetServer.loadAndRun(wa, args, client)
      case _ =>
    }
  }

  private def compileJpsLogic(command: CompileServerCommand.CompileJps, client: Client): Unit = {
    val CompileServerCommand.CompileJps(projectPath, globalOptionsPath, dataStorageRootPath) = command
    val dataStorageRoot = new File(dataStorageRootPath)
    val loader = new JpsModelLoaderImpl(projectPath, globalOptionsPath, false, null)
    val buildRunner = new BuildRunner(loader)
    var compiledFiles = Set.empty[File]
    val messageHandler = new MessageHandler {
      override def processMessage(msg: BuildMessage): Unit = msg match {
        case customMessage: CustomBuilderMessage =>
          CompilerEvent.fromCustomMessage(customMessage).foreach {
            case CompilerEvent.MessageEmitted(_, _, msg) => client.message(msg)
            case CompilerEvent.CompilationFinished(_, _, sources) => compiledFiles ++= sources
            case _ => ()
          }
        case progressMessage: ProgressMessage =>
          val text = Option(progressMessage.getMessageText).getOrElse("")
          val done = Option(progressMessage.getDone).filter(_ >= 0.0)
          client.progress(text, done)
        case _ =>
          ()
      }
    }
    val descriptor = buildRunner.load(messageHandler, dataStorageRoot, new BuildFSState(true))
    val forceBuild = false
    val scopes = CmdlineProtoUtil.createAllModulesScopes(forceBuild)

    client.compilationStart()
    try {
      buildRunner.runBuild(
        descriptor,
        () => client.isCanceled,
        null,
        messageHandler,
        BuildType.BUILD,
        scopes,
        true
      )
    } finally {
      client.compilationEnd(compiledFiles)
      descriptor.release()
    }
  }

  private def getMetricsLogic(command: CompileServerCommand.GetMetrics, client: Client): Unit = {
    val runtime = Runtime.getRuntime
    val metrics = CompileServerMetrics(
      heapUsed = runtime.totalMemory() - runtime.freeMemory(),
      maxHeapSize = maxHeapSize
    )
    client.metrics(metrics)
  }

  private def startMeteringLogic(command: CompileServerCommand.StartMetering, client: Client): Unit = {
    val CompileServerCommand.StartMetering(meteringInterval) = command
    MeteringScheduler.start(meteringInterval)
  }

  private def endMeteringLogic(command: CompileServerCommand.EndMetering, client: Client): Unit = {
    val result = MeteringScheduler.stop()
    client.meteringInfo(result)
  }

  private def parseArgs(command: String, argsEncoded: Seq[String]): Try[ArgsParsed] = {
    val args = argsEncoded.map(decodeArgument)
    args match {
      case Seq(token, other@_*) => CompileServerCommandParser.parse(command, other).map(ArgsParsed(token, _))
      case _                    => Failure(new IllegalArgumentException(s"arguments are empty"))
    }
  }

  private def decodeArgument(argEncoded: String): String = {
    val decoded = Base64.getDecoder.decode(argEncoded.getBytes)
    val str = new String(decoded, StandardCharsets.UTF_8)
    if (str == SerializationUtils.EmptyArgumentStub) "" else str
  }

  @throws(classOf[TokenVerificationException])
  private def validateToken(path: Path, actualToken: String): Unit = {
    if (!path.toFile.exists) {
      throw new TokenVerificationException("Token not found: " + path)
    }

    val expectedToken = try {
      new String(Files.readAllBytes(path))
    } catch {
      case _: IOException =>
        throw new TokenVerificationException("Cannot read token: " + path)
    }

    if (!expectedToken.equals(actualToken)) {
      throw new TokenVerificationException("Token is incorrect: " + actualToken)
    }
  }

  private class TokenVerificationException(message: String) extends Exception(message)

  private def cancelShutdownTimer(): Unit = synchronized {
    if (shutdownTimer != null) {
      shutdownTimer.cancel()
      shutdownTimer = null
    }
  }

  private def resetShutdownTimer(context: NGContext): Unit = {
    val delay = Option(System.getProperty("shutdown.delay")).map(_.toInt)
    delay.foreach { t =>
      val delayMs = t * 60 * 1000
      val shutdownTask = new TimerTask {
        override def run(): Unit = context.getNGServer.shutdown(true)
      }

      synchronized {
        cancelShutdownTimer()
        shutdownTimer = new Timer()
        shutdownTimer.schedule(shutdownTask, delayMs)
      }
    }
  }
}

object MeteringScheduler {

  private val lock = new Object
  @volatile private var executor: ScheduledExecutorService = _
  @volatile private var meteringInfo: CompileServerMeteringInfo = _

  final case class ArgsParsed(token: String, command: CompileServerCommand)

  def start(meteringInterval: FiniteDuration): Unit = lock.synchronized {
    executor = Executors.newScheduledThreadPool(1)
    meteringInfo = CompileServerMeteringInfo(0, 0)
    executor.scheduleWithFixedDelay({ () =>
      val currentParallelism = Main.getCurrentParallelism()
      val newMaxParallelism = math.max(meteringInfo.maxParallelism, currentParallelism)

      val currentHeapSizeMb = (Runtime.getRuntime.totalMemory / 1024 / 1024).toInt
      val newMaxHeapSizeMb = math.max(meteringInfo.maxHeapSizeMb, currentHeapSizeMb)
      
      // TODO more useful metrics

      meteringInfo = meteringInfo.copy(
        maxParallelism = newMaxParallelism,
        maxHeapSizeMb = newMaxHeapSizeMb
      )
    }, 0, meteringInterval.length, meteringInterval.unit)
  }

  def stop(): CompileServerMeteringInfo = lock.synchronized {
    Option(executor).foreach(_.awaitTermination(2, TimeUnit.SECONDS))
    val result = Option(meteringInfo)
    executor = null
    meteringInfo = null
    result.getOrElse(throw new IllegalStateException("MeteringScheduler wasn't started"))
  }
}
