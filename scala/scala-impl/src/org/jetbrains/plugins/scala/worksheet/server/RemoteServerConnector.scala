package org.jetbrains.plugins.scala
package worksheet.server

import java.io._
import java.nio.charset.StandardCharsets
import java.util.Base64

import com.intellij.compiler.CompilerMessageImpl
import com.intellij.openapi.compiler.{CompilerMessage, CompilerMessageCategory, CompilerPaths}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.scala.Client.PosInfo
import org.jetbrains.jps.incremental.scala.remote.CommandIds
import org.jetbrains.jps.incremental.scala.{Client, DummyClient}
import org.jetbrains.plugins.scala.compiler.data.worksheet.{WorksheetArgs, WorksheetArgsPlain, WorksheetArgsRepl}
import org.jetbrains.plugins.scala.compiler.{CompilationProcess, NonServerRunner, RemoteServerConnectorBase, RemoteServerRunner}
import org.jetbrains.plugins.scala.extensions.LoggerExt
import org.jetbrains.plugins.scala.lang.psi.api.ScFile
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettings
import org.jetbrains.plugins.scala.util.ScalaPluginJars
import org.jetbrains.plugins.scala.worksheet.actions.WorksheetFileHook
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetDefaultSourcePreprocessor
import org.jetbrains.plugins.scala.worksheet.server.RemoteServerConnector._
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings

// TODO: split to REPL and PLAIN args, change serialization format
// TODO: clean up this shit with arguments, half in constructor, half in method call
private[worksheet]
final class RemoteServerConnector(
  module: Module,
  worksheetPsiFile: ScFile,
  args: RemoteServerConnector.Args,
  makeType: WorksheetMakeType
) extends RemoteServerConnectorBase(
  module,
  filesToCompile = args.compiledFile.map(Seq(_)),
  outputDir = args.compilationOutputDir.getOrElse(new File("")),
  needCheck = args.compiledFile.nonEmpty
) {

  override protected def compilerSettings: ScalaCompilerSettings =
    WorksheetFileSettings(worksheetPsiFile).getCompilerProfile.getSettings

  override val worksheetArgs: Option[WorksheetArgs] =
    makeType match {
      case OutOfProcessServer => None
      case _ =>
        val argsTransformed = args match {
          case Args.PlainModeArgs(sourceFile, outputDir, className) =>
            Some(WorksheetArgsPlain(
              className,
              ScalaPluginJars.runnersJar,
              sourceFile,
              sourceFile.getName,
              outputDir +: outputDirs,
            ))
          case Args.ReplModeArgs(path, codeChunk) =>
            Some(WorksheetArgsRepl(
              sessionId = path,
              codeChunk,
              continueOnChunkError = Registry.is(WorksheetContinueOnFirstFailure),
              outputDirs
            ))
          case Args.CompileOnly(_, _) =>
            None // just compile (data is taken from CompilationData)
        }
        argsTransformed
    }

  override val scalaParameters: Seq[String] = {
    val options = super.scalaParameters
    if (module.hasScala3) {
      val extraOptions = Seq(
        "-color:never", // by default dotty prints lots of color, can't handle for now
        "-noindent", "-old-syntax" // do avoid "Line is indented too far to the left" warnings
      )
      options.filterNot(_.startsWith("-g:")) ++ extraOptions
    } else {
      options
    }
  }

  private def project = module.getProject

  def compileAndRun(
    originalFile: VirtualFile,
    consumer: RemoteServerConnector.CompilerInterface
  )(callback: RemoteServerConnectorResult => Unit): Unit = {
    val client = new MyTranslatingClient(project, originalFile, consumer)
    compileAndRun(originalFile, client)(callback)
  }

  // TODO: make something more advanced than just `callback: Runnable`: error reporting, Future, Task, etc...
  // TODO: add logging across all these callbacks in RunWorksheetAction, WorksheetCompiler, RemoteServerConnector...
  // NOTE: for now this method is non-blocking for runType == NonServer and blocking for other run types
  def compileAndRun(
    originalFile: VirtualFile, // TODO: looks like no need in this parameter
    client: Client
  )(callback: RemoteServerConnectorResult => Unit): Unit = {
    Log.debugSafe(s"compileAndRun: originalFile = $originalFile")
    val process = {
      val worksheetProcess: CompilationProcess = makeType match {
        case InProcessServer | OutOfProcessServer =>
          val runner = new RemoteServerRunner(project)
          val argumentsFinal = argumentsRaw
          runner.buildProcess(CommandIds.Compile, argumentsFinal, client)

        case NonServer =>
          val argumentsFinal = NoToken +: argumentsRaw
          val argumentsEncoded = argumentsFinal.map { arg =>
            val argFixed = if(arg.isEmpty) "#STUB#" else arg
            Base64.getEncoder.encodeToString(argFixed.getBytes(StandardCharsets.UTF_8))
          }
          val runner = new NonServerRunner(project)
          runner.buildProcess(argumentsEncoded, client)
      }

      if (worksheetProcess == null) {
        callback(RemoteServerConnectorResult.CantInitializeProcessError)
        return
      }

      WorksheetFileHook.updateStoppableProcess(originalFile, Some(() => worksheetProcess.stop()))
      worksheetProcess.addTerminationCallback { exception =>
        WorksheetFileHook.updateStoppableProcess(originalFile, None)

        val result = exception match {
          case Some(ex) => RemoteServerConnectorResult.ProcessTerminatedError(ex)
          case _        => RemoteServerConnectorResult.Done
        }
        callback.apply(result)
      }

      worksheetProcess
    }

    // exceptions thrown inside the process should be propagated to callback via termination callback
    process.run()
  }

  private def outputDirs: Seq[File] = {
    val modules = ModuleRootManager.getInstance(module).getDependencies :+ module
    val strings = modules.map(CompilerPaths.getModuleOutputPath(_, false))
    strings.map(new File(_))
  }
}

//private[worksheet]
object RemoteServerConnector {

  private val Log = Logger.getInstance(this.getClass)

  sealed trait Args {
    final def compiledFile: Option[File] = this match {
      case Args.PlainModeArgs(sourceFile, _, _) => Some(sourceFile)
      case Args.CompileOnly(sourceFile, _)      => Some(sourceFile)
      case Args.ReplModeArgs(_, _)              => None
    }
    final def compilationOutputDir: Option[File] = this match {
      case Args.PlainModeArgs(_, outputDir, _) => Some(outputDir)
      case Args.CompileOnly(_, outputDir)      => Some(outputDir)
      case _                                   => None
    }
  }
  object Args {
    final case class PlainModeArgs(sourceFile: File, outputDir: File, className: String) extends Args
    final case class ReplModeArgs(path: String, codeChunk: String) extends Args
    final case class CompileOnly(sourceFile: File, outputDir: File) extends Args
  }

  sealed trait RemoteServerConnectorResult
  object RemoteServerConnectorResult {
    case object Done extends RemoteServerConnectorResult

    sealed trait Error extends RemoteServerConnectorResult
    sealed trait UnhandledError extends Error
    final case class ProcessTerminatedError(cause: Throwable) extends UnhandledError
    final case object CantInitializeProcessError extends UnhandledError
    final case class ExpectedError(cause: Throwable) extends UnhandledError
    final case class UnexpectedError(cause: Throwable) extends UnhandledError
  }

  private class MyTranslatingClient(project: Project, worksheet: VirtualFile, consumer: CompilerInterface) extends DummyClient {
    private val endMarker = WorksheetDefaultSourcePreprocessor.ServiceMarkers.END_GENERATED_MARKER

    override def progress(text: String, done: Option[Float]): Unit =
      consumer.progress(text, done)

    override def trace(exception: Throwable): Unit =
      consumer.trace(exception)

    override def internalDebug(text: String): Unit =
      Log.debug(text)

    override def message(msg: Client.ClientMsg): Unit = {
      val Client.ClientMsg(kind, text, source, PosInfo(line, column, _), _) = msg
      val lines = (if (text == null) "" else text).split("\n")
      val linesLength = lines.length

      val differ = if (linesLength > 2) {
        val endLineIdx = lines(linesLength - 2).indexOf(endMarker)
        if (endLineIdx != -1) {
          endLineIdx + endMarker.length
        } else 0
      } else 0

      val finalText = if (differ == 0) text else {
        val buffer = new StringBuilder

        for (j <- 0 until (linesLength - 2)) {
          buffer.append(lines(j)).append("\n")
        }

        val lines1 = lines(linesLength - 1)

        buffer
          .append(lines(linesLength - 2).substring(differ)).append("\n")
          .append(if (lines1.length > differ) lines1.substring(differ) else lines1).append("\n")

        buffer.toString()
      }

      // TODO: current line & column calculation are broken
      val line1 = line.map(i => i - 4).map(_.toInt).getOrElse(-1)
      val column1 = column.map(_ - differ).map(_.toInt).getOrElse(-1)

      val category = toCompilerMessageCategory(kind)

      val message = new CompilerMessageImpl(project, category, finalText, worksheet, line1, column1, null)
      consumer.message(message)
    }

    private def toCompilerMessageCategory(kind: Kind): CompilerMessageCategory = {
      import BuildMessage.Kind._
      kind match {
        case INFO | JPS_INFO | OTHER        => CompilerMessageCategory.INFORMATION
        case ERROR | INTERNAL_BUILDER_ERROR => CompilerMessageCategory.ERROR
        case PROGRESS                       => CompilerMessageCategory.STATISTICS
        case WARNING                        => CompilerMessageCategory.WARNING
      }
    }

    override def worksheetOutput(text: String): Unit =
      consumer.worksheetOutput(text)
  }

  // Worksheet Integration Tests rely on that this is the main entry point for all compiler messages
  trait CompilerMessagesConsumer {
    def message(message: CompilerMessage): Unit
  }

  trait CompilerInterface extends CompilerMessagesConsumer {
    def progress(text: String, done: Option[Float]): Unit

    def worksheetOutput(text: String): Unit
    def trace(thr: Throwable): Unit

    def isCompiledWithErrors: Boolean
  }

  // to test restoring of compiler messages positions in original worksheet file in one test method
  @TestOnly
  val WorksheetContinueOnFirstFailure = "scala.worksheet.continue.evalutaion.on.first.expression.failure"
}
