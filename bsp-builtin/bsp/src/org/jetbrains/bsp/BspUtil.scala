package org.jetbrains.bsp

import com.intellij.build.events.impl.{FailureResultImpl, SkippedResultImpl, SuccessResultImpl}
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressIndicator}
import com.intellij.openapi.project.{Project, ProjectUtil}
import com.intellij.openapi.roots.CompilerProjectExtension
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.jetbrains.annotations.Nls
import org.jetbrains.bsp.data.BspProjectData
import org.jetbrains.bsp.project.BspExternalSystemUtil
import org.jetbrains.bsp.settings.{BspProjectSettings, BspSettings}
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.BuildReporter
import org.jetbrains.plugins.scala.extensions.PathExt

import java.net.URI
import java.nio.file.{Path, Paths}
import java.util.concurrent.CompletableFuture
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.util.{Failure, Success, Try}

object BspUtil {

  private val log = Logger.getInstance(getClass)

  val BloopConfigDirName = ".bloop"

  /** BSP Workspaces in modules managed by project. */
  def workspaces(project: Project): Set[Path] =
    ModuleManager.getInstance(project).getModules.toList
      .map { module =>
        val modulePath = ExternalSystemApiUtil.getExternalProjectPath(module)
        Paths.get(modulePath)
      }
      .toSet

  def isBloopConfigFile(file: Path): Boolean = {
    file.isRegularFile &&
      file.getParent.getFileName.toString == BloopConfigDirName &&
      file.getFileName.toString.endsWith(".json")
  }

  def bloopConfigDir(workspace: Path): Option[Path] = {
    val bloopDir = workspace.resolve(BloopConfigDirName)

    if (bloopDir.isDirectory)
      Some(bloopDir.toCanonicalPath)
    else None
  }

  private[bsp] def isBloopConfigDir(file: VirtualFile): Boolean =
    file.getName == BspUtil.BloopConfigDirName && file.isDirectory

  def isBspModule(module: Module): Boolean =
    ExternalSystemApiUtil.isExternalSystemAwareModule(BSP.ProjectSystemId, module)

  def isBspProject(project: Project): Boolean = {
    val settings = bspSettings(project).getLinkedProjectsSettings
    !settings.isEmpty
  }

  def bspSettings(project: Project): BspSettings =
    ExternalSystemApiUtil
      .getSettings(project, BSP.ProjectSystemId)
      .asInstanceOf[BspSettings]

  def compilerOutputDirFromConfig(base: Path): Option[Path] = {
    val vfm = VirtualFileManager.getInstance()
    for {
      projectDir <- Option(vfm.findFileByUrl(base.toUri.toString)) // path.toUri is rendered with :// separator which findFileByUrl needs
      project <- Option(ProjectUtil.guessProjectForFile(projectDir))
      cpe = CompilerProjectExtension.getInstance(project)
      output <- Option(cpe.getCompilerOutput)
    } yield Path.of(output.getCanonicalPath)
  }

  implicit class ResponseErrorExceptionOps(err: ResponseErrorException) {
    def toBspError: BspResponseError = {
      BspResponseError(s"bsp error: ${err.getMessage} (${err.getResponseError.getCode})", err.getResponseError)
    }
  }

  implicit class StringOps(str: String) {
    def toURI: URI = new URI(str)
  }

  implicit class CompletableFutureOps[T](cf: CompletableFuture[T]) {
    def catchBspErrors : CompletableFuture[Try[T]] = cf.handle { (result, error) =>
      if (error != null) error match {
        case responseError: ResponseErrorException =>
          Failure(responseError.toBspError)
        case other: Throwable => throw other
      } else Success(result)
    }

    def reportFinished(reporter: BuildReporter,
                       eventId: EventId,
                       @Nls successMsg: String,
                       @Nls failMsg: String
                      ): CompletableFuture[T] = {
      cf.thenAccept {
        case Success(_) =>
          reporter.finishTask(eventId, successMsg, new SuccessResultImpl(true))
        case Failure(BspResponseError(message, error)) =>
          if (error.getCode == ResponseErrorCode.MethodNotFound.getValue) {
            reporter.finishTask(eventId, "unsupported method", new SkippedResultImpl)
          } else {
            val reportMsg = failMsg + "\n" + message
            reporter.finishTask(eventId, reportMsg, new FailureResultImpl(reportMsg))
          }
        case Failure(x) =>
          reporter.finishTask(eventId, failMsg, new FailureResultImpl(failMsg, x))
        case _ =>
          reporter.finishTask(eventId, successMsg, new SuccessResultImpl(true))
      }
      cf
    }
  }

  /**
   * Try to find a file by its name in a given directory.
   * Returns it only if it exists and is a regular file.
   */
  def findFileByName(dir: Path, name: String): Option[Path] = {
    val candidate = dir / name
    Option.when(candidate.isRegularFile)(candidate)
  }

  private def isBspScalaCliProjectImpl(project: Project, rootProjectPath: Option[String]): Boolean =
    BspExternalSystemUtil.getBspProjectData(project, rootProjectPath) match {
      case Some(BspProjectData(_, _, "scala-cli")) => true
      case _ => false
    }

  def isBspScalaCliProject(project: Project): Boolean =
    isBspScalaCliProjectImpl(project, None)

  def isBspScalaCliProject(project: Project, rootProjectPath: String): Boolean =
    isBspScalaCliProjectImpl(project, Some(rootProjectPath))

  /**
   * Checks whether a specified directory contains at least one file with a name from a given sequence of file names.
   */
  def directoryContainsFile(directory: Path, fileNames: String*): Boolean =
    fileNames.exists(name => (directory / name).isRegularFile)

  /**
   * Checks whether a command-line tool is installed by invoking its version command.
   *
   * @param directory directory in which the check will be executed
   * @param toolCommand executable name (e.g. "scala-cli", "mill")
   */
  @RequiresBackgroundThread
  def isToolInstalledCheckViaVersion(directory: Path, indicator: ProgressIndicator, toolCommand: String*): Boolean = {
    val work = runCommand(directory, indicator, (toolCommand :+ "--version")*)
    work.fold(
      exc => {
        log.error(s"The $toolCommand is not installed in $directory - ${exc.getMessage}")
        false
      },
      _ => true
    )
  }

  /**
   * @return Right, if the process exit value is 0; otherwise, return Left with the exception.
   */
  @RequiresBackgroundThread
  def runCommand(directory: Path, indicator: ProgressIndicator, command: String*): Either[Throwable, Int] = {
    val stderr = new StringBuilder
    val work = Try {
      val generalCommandLine = new GeneralCommandLine(command.asJava)
        .withWorkDirectory(directory.toString)

      val handler = new CapturingProcessHandler(generalCommandLine)
      val timeout = 2.minute
      val output = handler.runProcessWithProgressIndicator(indicator, timeout.toMillis.toInt)

      stderr.append(output.getStderr.trim)

      output.getExitCode
      if (output.isCancelled) {
        throw new ProcessCanceledException()
      }

      output.getExitCode
    }

    work match {
      case Success(0) => Right(0)
      case Success(_) => Left(new Exception(stderr.toString()))
      case Failure(exc) => Left(exc)
    }
  }

  private[bsp] def getBspProjectSettings(project: Project, linkedProjectPath: Path): Option[BspProjectSettings] =
    Option(BspSettings.getInstance(project).getLinkedProjectSettings(linkedProjectPath.toCanonicalPath.toString))
}
