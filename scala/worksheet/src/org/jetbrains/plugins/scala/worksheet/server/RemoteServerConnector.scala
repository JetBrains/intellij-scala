package org.jetbrains.plugins.scala
package worksheet.server

import java.io._
import com.intellij.openapi.compiler.{CompilerMessage, CompilerPaths}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.remote.CommandIds
import org.jetbrains.plugins.scala.compiler.data.worksheet.WorksheetArgs
import org.jetbrains.plugins.scala.compiler.{CompilationProcess, NonServerRunner, RemoteServerConnectorBase, RemoteServerRunner}
import org.jetbrains.plugins.scala.console.configuration.ScalaSdkJLineFixer
import org.jetbrains.plugins.scala.console.configuration.ScalaSdkJLineFixer.JlineResolveResult
import org.jetbrains.plugins.scala.extensions.LoggerExt
import org.jetbrains.plugins.scala.lang.psi.api.ScFile
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettings
import org.jetbrains.plugins.scala.util.ScalaPluginJars
import org.jetbrains.plugins.scala.worksheet.{WorksheetCompilerExtension, WorksheetUtils}
import org.jetbrains.plugins.scala.worksheet.actions.WorksheetFileHook
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
  outputDir = args.compilationOutputDir.getOrElse(new File(""))
) {

  override protected def compilerSettings: ScalaCompilerSettings =
    WorksheetFileSettings(worksheetPsiFile).getCompilerProfile.getSettings

  override val worksheetArgs: Option[WorksheetArgs] =
    makeType match {
      case OutOfProcessServer => None
      case _ =>
        val argsTransformed = args match {
          case Args.PlainModeArgs(sourceFile, outputDir, className) =>
            Some(WorksheetArgs.RunPlain(
              className,
              ScalaPluginJars.runnersJar,
              sourceFile,
              sourceFile.getName,
              outputDir +: outputDirs,
            ))
          case Args.ReplModeArgs(path, dropCachedReplInstance, codeChunk) =>
            Some(WorksheetArgs.RunRepl(
              sessionId = path,
              codeChunk,
              dropCachedReplInstance,
              continueOnChunkError = WorksheetUtils.continueWorksheetEvaluationOnExpressionFailure,
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

    ScalaSdkJLineFixer.validateJLineInCompilerClassPath(module) match {
      case JlineResolveResult.NotRequired =>
      case JlineResolveResult.RequiredFound(file) =>
        additionalCompilerClasspath = Seq(file)
      case JlineResolveResult.RequiredNotFound =>
        callback(RemoteServerConnectorResult.RequiredJLineIsMissingFromClasspathError(module))
        return
    }

    val process: CompilationProcess = {
      val argumentsRaw = arguments.asStrings

      val worksheetProcess: CompilationProcess = makeType match {
        case InProcessServer | OutOfProcessServer =>
          val runner = new RemoteServerRunner(project)
          runner.buildProcess(CommandIds.Compile, argumentsRaw, client)

        case NonServer =>
          val runner = new NonServerRunner(project)
          runner.buildProcess(argumentsRaw, client)
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
    strings.map(new File(_)).toSeq
  }

  override protected def assemblyRuntimeClasspath(): Seq[File] = {
    val extensionCp = WorksheetCompilerExtension.worksheetClasspath(module)
    extensionCp.getOrElse {
      super.assemblyRuntimeClasspath()
    }
  }
}

//private[worksheet]
object RemoteServerConnector {

  private val Log = Logger.getInstance(this.getClass)

  sealed trait Args {
    final def compiledFile: Option[File] = this match {
      case Args.PlainModeArgs(sourceFile, _, _) => Some(sourceFile)
      case Args.CompileOnly(sourceFile, _)      => Some(sourceFile)
      case Args.ReplModeArgs(_, _, _)              => None
    }
    final def compilationOutputDir: Option[File] = this match {
      case Args.PlainModeArgs(_, outputDir, _) => Some(outputDir)
      case Args.CompileOnly(_, outputDir)      => Some(outputDir)
      case _                                   => None
    }
  }
  object Args {
    final case class PlainModeArgs(sourceFile: File, outputDir: File, className: String) extends Args
    final case class ReplModeArgs(path: String, dropCachedReplInstance: Boolean, codeChunk: String) extends Args
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
    final case class RequiredJLineIsMissingFromClasspathError(module: Module) extends UnhandledError  // (SCL-15818, SCL-15948)
    final case class UnexpectedError(cause: Throwable) extends UnhandledError
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
}
