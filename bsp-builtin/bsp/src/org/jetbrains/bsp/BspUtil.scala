package org.jetbrains.bsp

import java.io.File
import java.net.URI
import java.nio.file.{Path, Paths}
import java.util.concurrent.CompletableFuture
import com.intellij.build.events.impl.{FailureResultImpl, SkippedResultImpl, SuccessResultImpl}
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.{Project, ProjectUtil}
import com.intellij.openapi.roots.CompilerProjectExtension
import com.intellij.openapi.vfs.VirtualFileManager
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.jetbrains.annotations.Nls
import org.jetbrains.bsp.settings.BspSettings
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.BuildReporter

import scala.util.{Failure, Success, Try}

object BspUtil {
  
  val BloopConfigDirName = ".bloop"

  /** BSP Workspaces in modules managed by project. */
  def workspaces(project: Project): Set[Path] =
    ModuleManager.getInstance(project).getModules.toList
      .map { module =>
        val modulePath = ExternalSystemApiUtil.getExternalProjectPath(module)
        Paths.get(modulePath)
      }
      .toSet

  def isBloopConfigFile(file: File): Boolean = {
    file.isFile &&
      file.getParentFile.getName == BloopConfigDirName &&
      file.getName.endsWith(".json")
  }

  def bloopConfigDir(workspace: File): Option[File] = {
    val bloopDir = new File(workspace, BloopConfigDirName)

    if (bloopDir.isDirectory)
      Some(bloopDir.getCanonicalFile)
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

  def compilerOutputDirFromConfig(base: File): Option[File] = {
    val vfm = VirtualFileManager.getInstance()
    for {
      projectDir <- Option(vfm.findFileByUrl(base.toPath.toUri.toString)) // path.toUri is rendered with :// separator which findFileByUrl needs
      project <- Option(ProjectUtil.guessProjectForFile(projectDir))
      cpe = CompilerProjectExtension.getInstance(project)
      output <- Option(cpe.getCompilerOutput)
    } yield new File(output.getCanonicalPath)
  }

  implicit class ResponseErrorExceptionOps(err: ResponseErrorException) {
    def toBspError: BspResponseError = {
      BspResponseError(s"bsp error: ${err.getMessage} (${err.getResponseError.getCode})", err.getResponseError)
    }
  }

  implicit class StringOps(str: String) {
    def toURI: URI = new URI(str)
  }

  implicit class URIOps(uri: URI) {
    def toFile: File = Paths.get(uri).toFile
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

}
