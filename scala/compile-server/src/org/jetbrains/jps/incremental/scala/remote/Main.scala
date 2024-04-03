package org.jetbrains.jps.incremental.scala.remote

import com.facebook.nailgun.{NGContext, NGServer}
import com.intellij.openapi.application.PathManager
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.data.CompileServerCommandParser
import org.jetbrains.jps.incremental.scala.local.worksheet.WorksheetServer
import org.jetbrains.jps.incremental.scala.local.{Cache, LocalServer}
import org.jetbrains.jps.incremental.scala.utils.CompileServerSharedMessages
import org.jetbrains.plugins.scala.compiler.data.serialization.SerializationUtils
import org.jetbrains.plugins.scala.compiler.data.worksheet.WorksheetArgs
import org.jetbrains.plugins.scala.compiler.data.{Arguments, ComputeStampsArguments, ExpressionEvaluationArguments}
import org.jetbrains.plugins.scala.server.CompileServerToken

import java.io._
import java.lang.reflect.Method
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicInteger
import java.util.{Timer, TimerTask}
import scala.annotation.unused
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters._
import scala.util.matching.Regex
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
  @unused("used via reflection in org.jetbrains.plugins.scala.nailgun.Utils.setupScalaCompileServerSystemDir")
  def setupScalaCompileServerSystemDir(scalaCompileServerSystemDir: Path): Unit = {
    this.scalaCompileServerSystemDir = scalaCompileServerSystemDir
  }

  @unused("used via reflection from org.jetbrains.plugins.scala.nailgun.Utils.setupServerShutdownTimer")
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
  //noinspection ScalaUnusedSymbol
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
        } catch {
          case t: Throwable =>
            client.trace(t)
        } finally {
          currentParallelism.decrementAndGet()
        }
      else
        action

    decorated {
      command match {
        case CompileServerCommand.Compile(arguments) =>
          compileLogic(arguments, client)
        case CompileServerCommand.ComputeStamps(arguments) =>
          computeStampsLogic(arguments, client)
        case compileJps: CompileServerCommand.CompileJps =>
          Jps.compileJpsLogic(compileJps, client, scalaCompileServerSystemDir)
        case CompileServerCommand.EvaluateExpression(args) =>
          evaluateExpressionLogic(args)
        case CompileServerCommand.GetMetrics =>
          getMetricsLogic(client)
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

  private def computeStampsLogic(args: ComputeStampsArguments, client: Client): Unit = {
    val ComputeStampsArguments(outputFiles, analysisFile) = args
    server.computeStamps(outputFiles, analysisFile, client)
  }

  private val expressionCompilerCache: Cache[Seq[Path], (AnyRef, Method)] = new Cache(3)

  private val scala3CompilerJarName: String = "scala3-compiler_3"

  private val scalaVersionRegex: Regex = s"""$scala3CompilerJarName-(\\d)\\.(\\d+)\\.(\\d+).*\\.jar""".r

  private val scala3StableVersions: Set[String] = Set(
    "3.0.0", "3.0.1", "3.0.2",
    "3.1.0", "3.1.1", "3.1.2", "3.1.3",
    "3.2.0", "3.2.1", "3.2.2",
    "3.3.0", "3.3.1", "3.3.2", "3.3.3",
    "3.4.0", "3.4.1"
  )

  private val scala3FallbackVersion: String = "3.4.1"

  private def evaluateExpressionLogic(args: ExpressionEvaluationArguments): Unit = {
    val ExpressionEvaluationArguments(outDir, classpath, scalacOptions, source, line, expression, localVariableNames, packageName) = args

    val scalaVersion =
      classpath
        .collectFirst {
          case p if p.getFileName.toString.startsWith(scala3CompilerJarName) => p.getFileName.toString
        }
        .collect {
          case scalaVersionRegex(x, y, z) => s"$x.$y.$z"
        }.filter(scala3StableVersions).getOrElse(scala3FallbackVersion)

    val (instance, method) = expressionCompilerCache.getOrUpdate(classpath) { () =>
      val path = PathManager.getJarForClass(this.getClass)
        .getParent.getParent.getParent
        .resolve("debugger")
        .resolve(s"scala-expression-compiler_$scalaVersion.jar")
      val classLoader = new URLClassLoader((classpath :+ path).map(_.toUri.toURL).toArray, this.getClass.getClassLoader)
      val bridgeClass = Class.forName("dotty.tools.dotc.ExpressionCompilerBridge", true, classLoader)
      val instance = bridgeClass.getDeclaredConstructor().newInstance().asInstanceOf[AnyRef]
      val method = bridgeClass.getMethods.find(_.getName == "run").get
      (instance, method)
    }

    val consumer: java.util.function.Consumer[String] = _ => ()

    method.invoke(instance, outDir, "CompiledExpression", classpath.mkString(File.pathSeparator), scalacOptions.toArray, source, line,
      expression, localVariableNames.asJava, packageName, consumer, false)
  }

  private def getMetricsLogic(client: Client): Unit = {
    val runtime = Runtime.getRuntime
    val currentHeapSize = runtime.totalMemory()
    val metrics = CompileServerMetrics(
      heapUsed = currentHeapSize - runtime.freeMemory(),
      currentHeapSize = currentHeapSize,
      maxHeapSize = maxHeapSize,
      currentParallelism = getCurrentParallelism
    )
    client.metrics(metrics)
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
        shutdownTimer = new Timer(true)
        shutdownTimer.schedule(shutdownTask, t.toMillis)
      }
    }
  }

  private def shutdownDelay: Option[FiniteDuration] =
    Option(System.getProperty("shutdown.delay.seconds")).map(_.toInt.seconds)

  final case class ArgsParsed(token: String, command: CompileServerCommand)
}
