package org.jetbrains.jps.incremental.scala.remote

import com.facebook.nailgun.{NGContext, NGServer}
import org.jetbrains.jps.api.{BuildType, CmdlineProtoUtil, GlobalOptions}
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.cmdline.{BuildRunner, JpsModelLoaderImpl}
import org.jetbrains.jps.incremental.fs.BuildFSState
import org.jetbrains.jps.incremental.messages.{BuildMessage, CustomBuilderMessage, ProgressMessage}
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.data.CompileServerCommandParser
import org.jetbrains.jps.incremental.scala.local.LocalServer
import org.jetbrains.jps.incremental.scala.local.worksheet.WorksheetServer
import org.jetbrains.jps.incremental.scala.remote.MeteringScheduler.ArgsParsed
import org.jetbrains.jps.incremental.scala.utils.CompileServerSharedMessages
import org.jetbrains.jps.incremental.{MessageHandler, Utils}
import org.jetbrains.plugins.scala.compiler.CompilerEvent
import org.jetbrains.plugins.scala.compiler.data.Arguments
import org.jetbrains.plugins.scala.compiler.data.serialization.SerializationUtils
import org.jetbrains.plugins.scala.compiler.data.worksheet.WorksheetArgs
import org.jetbrains.plugins.scala.server.CompileServerToken

import java.io._
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}
import java.util.{Timer, TimerTask}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

/**
 * Nailgun Nail, used in:
 *
 * @see `org.jetbrains.plugins.scala.nailgun.NailgunRunner`
 * @see `org.jetbrains.plugins.scala.nailgun.NailgunMainLightRunner`
 * @see `org.jetbrains.plugins.scala.worksheet.server.NonServerRunner`
 */
object Main {

  /**
   * We need to remember original output streams, otherwise System.out/err can reference to stale [[com.facebook.nailgun.ThreadLocalPrintStream]] values
   * Please, see comment for the details https://youtrack.jetbrains.com/issue/SCL-19367#focus=Comments-27-5074050.0-0
   */
  private var originalStdOut: PrintStream = _
  private var originalStdErr: PrintStream = _

  private val server = new LocalServer()
  private val worksheetServer = new WorksheetServer

  private var shutdownTimer: Timer = _
  @volatile private var shutdownByTimout: Boolean = false

  private val maxHeapSize = Runtime.getRuntime.maxMemory()
  private val currentParallelism = new AtomicInteger(0)

  private var scalaCompileServerSystemDir: Path = _

  // NOTE: we can't merge all setup methods, because in MainLightRunner (NonServerRunner) nailgun classes are not available
  def setupScalaCompileServerSystemDir(scalaCompileServerSystemDir: Path): Unit = {
    this.scalaCompileServerSystemDir = scalaCompileServerSystemDir
    Utils.setSystemRoot(scalaCompileServerSystemDir.toFile)
  }

  def setupServerShutdownTimer(server: NGServer): Unit = {
    originalStdOut = System.out
    originalStdErr = System.err

    def assertDefaultStream(out: PrintStream, name: String): Unit = {
      val streamClass = originalStdOut.getClass
      assert(
        streamClass == classOf[java.io.PrintStream],
        s"Unexpected $name stream class: $streamClass. Please, ensure that 'setup' method is invoked before standard streams are patched with java.lang.System.set* methods"
      )
    }

    assertDefaultStream(originalStdOut, "stdout")
    assertDefaultStream(originalStdErr, "stderr")

    if (server != null) {
      // we need the server to shut down on timer even if no compilation was done (nailMain is not invoked)
      resetShutdownTimer(server)
    }
  }

  def getCurrentParallelism: Int = currentParallelism.get()

  /**
   * This method is called by NGServer
   *
   * @see [[http://www.martiansoftware.com/nailgun/quickstart.html]]<br>
   *      [[http://www.martiansoftware.com/nailgun/doc/javadoc/com/martiansoftware/nailgun/NGContext.html]]<br>
   *      [[com.facebook.nailgun.NGContext]]<br>
   *      [[com.facebook.nailgun.NGSession:153]]<br>
   *      [[com.facebook.nailgun.NGServer:198]]<br>
   */
  def nailMain(context: NGContext): Unit = {
    cancelShutdownTimer()
    try
      serverLogic(
        commandId = context.getCommand,
        argsEncoded = context.getArgs.toSeq,
        out = context.out,
        port = context.getNGServer.getPort,
        standalone = false
      )
    finally
      resetShutdownTimer(context)
  }

  // TODO: more reliable "unexpected process termination"  SCL-19367
  //noinspection ScalaUnusedSymbol
  def nailShutdown(server: NGServer): Unit = {
    import CompileServerSharedMessages._
    val details = if (shutdownByTimout) s" ($ProcessWasIdleFor ${shutdownDelay.getOrElse("<unknown>")})" else ""
    originalStdOut.println(CompileServerShutdownPrefix + s"$details")
    originalStdOut.flush() // just in case, System.exit (used in NGServer.shutdown) can skip flushing the streams
  }

  def main(args: Array[String]): Unit = {
    serverLogic(CommandIds.Compile, args.toIndexedSeq, System.out, -1, standalone = true)
  }

  private def serverLogic(commandId: String,
                          argsEncoded: Seq[String],
                          out: PrintStream,
                          port: Int,
                          standalone: Boolean): Unit = {
    if (scalaCompileServerSystemDir == null)
      throw new IllegalStateException("the 'setup' method must be invoked before compile server usage")
    val client = new EncodingEventGeneratingClient(out, standalone)
    val oldOut = System.out
    // Suppress any stdout data, interpret such data as error
    System.setOut(System.err)


    try {
      def traceStep(message: String): Unit =
        client.internalTrace(s"[main nail]: $message")
      traceStep("parsing arguments")

      val argsParsed = parseArgs(commandId, argsEncoded) match {
        case Success(result) =>
          result
        case Failure(error) =>
          client.trace(error)
          return
      }

      traceStep("validating token")
      // Don't check token in non-server mode
      if (port != -1) {
        try {
          val tokenPath = CompileServerToken.tokenPathForPort(scalaCompileServerSystemDir, port)
          validateToken(tokenPath, argsParsed.token)
        } catch {
          // We must abort the process on _any_ error
          case e: Throwable =>
            client.error(e.getMessage)
            return
        }
      }

      traceStep(s"handling command: $commandId")
      handleCommand(argsParsed.command, client)
      traceStep(s"done")
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
    val CompileServerCommand.CompileJps(projectPath, globalOptionsPath, dataStorageRootPath, externalProjectConfig, moduleNames) = command
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
    val descriptor = withModifiedExternalProjectPath(externalProjectConfig) {
      buildRunner.load(messageHandler, dataStorageRoot, new BuildFSState(true))
    }
    val forceBuild = false

    val scopes = Seq(
      CmdlineProtoUtil.createTargetsScope(JavaModuleBuildTargetType.PRODUCTION.getTypeId, moduleNames.asJava, forceBuild),
      CmdlineProtoUtil.createTargetsScope(JavaModuleBuildTargetType.TEST.getTypeId, moduleNames.asJava, forceBuild)
    ).asJava

    client.compilationStart()
    try {
      buildRunner.runBuild(
        descriptor,
        () => client.isCanceled,
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

  private val ExternalProjectConfigPropertyLock = new Object

  /**
   * In case project configuration is stored externally (outside `.idea` folder) we need to provide the path to the external storage.
   *
   * @see `org.jetbrains.jps.model.serialization.JpsProjectLoader.loadFromDirectory`
   * @see [[org.jetbrains.jps.model.serialization.JpsProjectLoader.resolveExternalProjectConfig]]
   * @see [[org.jetbrains.jps.api.GlobalOptions.EXTERNAL_PROJECT_CONFIG]]
   * @see `com.intellij.compiler.server.BuildManager.launchBuildProcess`
   * @see `org.jetbrains.plugins.scala.externalHighlighters.compiler.IncrementalCompiler.compile`
   */
  private def withModifiedExternalProjectPath[T](externalProjectConfig: Option[String])(body: => T): T = {
    externalProjectConfig match {
      case Some(value) =>
        //NOTE: We have use lock here because currently we can only pass the external project config path via System.get/setProperty
        //This can lead to issues when incremental compilation is triggered for several projects which use compiler-based highlighting
        //This is because Scala Compiler Server is currently reused between all projects and System.get/setProperty modifies global JVM state.
        //TODO: Ideally we would need some way to pass the value to JpsProjectLoader more transparently
        ExternalProjectConfigPropertyLock.synchronized {
          val Key = GlobalOptions.EXTERNAL_PROJECT_CONFIG
          val previousValue = System.getProperty(Key)
          try {
            System.setProperty(Key, value)
            body
          }
          finally {
            if (previousValue == null)
              System.clearProperty(Key)
            else
              System.setProperty(Key, previousValue)
          }
        }
      case _ =>
        body
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

  private def parseArgs(command: String, argsRaw: Seq[String]): Try[ArgsParsed] = {
    val args = argsRaw.map(normalizeArgument)
    args match {
      case Seq(token, other@_*) => CompileServerCommandParser.parse(command, other).map(ArgsParsed(token, _))
      case _                    => Failure(new IllegalArgumentException(s"arguments are empty"))
    }
  }

  private def normalizeArgument(arg: String): String =
    if (arg == SerializationUtils.EmptyArgumentStub) "" else arg

  @throws(classOf[TokenVerificationException])
  private def validateToken(path: Path, actualToken: String): Unit = {
    if (!path.toFile.exists) {
      throw new TokenVerificationException("Token not found: " + path)
    }

    val expectedToken = try {
      new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
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

  private def resetShutdownTimer(context: NGContext): Unit =
    resetShutdownTimer(context.getNGServer)

  private def resetShutdownTimer(server: NGServer): Unit = {
    val delay = shutdownDelay
    delay.foreach { t =>
      val shutdownTask = new TimerTask {
        override def run(): Unit = {
          shutdownByTimout = true
          server.shutdown()
        }
      }

      synchronized {
        cancelShutdownTimer()
        shutdownTimer = new Timer()
        shutdownTimer.schedule(shutdownTask, t.toMillis)
      }
    }
  }

  private def shutdownDelay: Option[FiniteDuration] =
    Option(System.getProperty("shutdown.delay.seconds")).map(_.toInt.seconds)
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
      val currentParallelism = Main.getCurrentParallelism
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
