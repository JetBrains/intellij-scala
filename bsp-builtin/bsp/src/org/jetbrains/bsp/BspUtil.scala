package org.jetbrains.bsp

import com.intellij.build.events.impl.{FailureResultImpl, SkippedResultImpl, SuccessResultImpl}
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.{Project, ProjectUtil}
import com.intellij.openapi.roots.CompilerProjectExtension
import com.intellij.openapi.vfs.VirtualFileManager
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.jetbrains.annotations.Nls
import org.jetbrains.bsp.data.BspProjectData
import org.jetbrains.bsp.project.BspExternalSystemUtil
import org.jetbrains.bsp.settings.BspSettings
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.BuildReporter
import org.jetbrains.plugins.scala.extensions.PathExt

import java.net.URI
import java.nio.file.{Path, Paths}
import java.util.concurrent.CompletableFuture
import scala.io.Source
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

  def findFileByName(dir: Path, name: String): Option[Path] =
    dir.children()
      .find(x => x.getFileName.toString == name && !x.isDirectory)

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
    directory.children()
      .exists(x => !x.isDirectory && fileNames.contains(x.getFileName.toString))

  /**
   *
   * @param directory where the tool installation will be checked
   */
  def checkIfToolIsInstalled(directory: Path, toolCommand: String): Boolean = {
    val work = runCommand(directory, toolCommand, "version")
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
  def runCommand(directory: Path, command: String*): Either[Throwable, Int] = {
    val stderr = new StringBuilder
    val work = Try {
      val generalCommandLine = new GeneralCommandLine(command.asJava)
        .withWorkDirectory(directory.toString)
      val process = generalCommandLine.toProcessBuilder.start()
      process.waitFor()

      val stderrText = Source.fromInputStream(process.getErrorStream).mkString.trim
      stderr.append(stderrText)

      process.exitValue()
    }

    work match {
      case Success(0) => Right(0)
      case Success(_) => Left(new Exception(stderr.toString()))
      case Failure(exc) => Left(exc)
    }
  }
}
