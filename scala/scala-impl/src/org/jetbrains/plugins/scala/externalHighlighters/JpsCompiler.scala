package org.jetbrains.plugins.scala.externalHighlighters

import java.io.File

import com.intellij.compiler.progress.CompilerTask
import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.util.io.PathKt
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.jps.incremental.scala.remote.{CommandIds, CompileServerCommand}
import org.jetbrains.jps.incremental.scala.{Client, DummyClient}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventListener, RemoteServerRunner}
import org.jetbrains.plugins.scala.util.CompilationId

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}
import scala.util.Try

trait JpsCompiler {
  def compile(): Unit

  def buildActionAllowed: Boolean

  def setBuildActionAllowed(value: Boolean): Unit
}

object JpsCompiler {

  def get(project: Project): JpsCompiler =
    ServiceManager.getService(project, classOf[JpsCompiler])
}

private class JpsCompilerImpl(project: Project)
  extends JpsCompiler {

  @volatile private var allowBuildAction: Boolean = false

  import JpsCompilerImpl.CompilationClient

  override def compile(): Unit = {
    val command = CommandIds.CompileJps
    val projectPath = project.getBasePath
    val globalOptionsPath = PathManager.getOptionsPath
    val dataStorageRootPath = Utils.getDataStorageRoot(
      new File(PathKt.getSystemIndependentPath(BuildManager.getInstance.getBuildSystemDirectory)),
      projectPath
    ).getCanonicalPath
    val args = CompileServerCommand.CompileJps(
      token = "",
      projectPath = projectPath,
      globalOptionsPath = globalOptionsPath,
      dataStorageRootPath = dataStorageRootPath
    ).asArgsWithoutToken
    val client = new CompilationClient(project)
    val promise = Promise[Unit]

    val compileWork: Runnable = { () =>
      setBuildActionAllowed(true)
      val result = Try(new RemoteServerRunner(project).buildProcess(command, args, client).runSync())
      promise.complete(result)
      setBuildActionAllowed(false)
    }
    val restartWork: Runnable = () => ()
    val task = new CompilerTask(project, ScalaBundle.message("highlighting.compilation"),
      true, false, true, true)
    task.start(compileWork, restartWork)
    Await.result(promise.future, Duration.Inf)
  }

  override def buildActionAllowed: Boolean =
    allowBuildAction

  override def setBuildActionAllowed(value: Boolean): Unit =
    allowBuildAction = value
}

private object JpsCompilerImpl {

  private class CompilationClient(project: Project)
    extends DummyClient {

    final val compilationId = CompilationId.generate()

    override def message(msg: Client.ClientMsg): Unit =
      sendEvent(CompilerEvent.MessageEmitted(compilationId, msg))

    override def compilationEnd(sources: Set[File]): Unit =
      sources.foreach { source =>
        sendEvent(CompilerEvent.CompilationFinished(compilationId, source))
      }

    def sendEvent(event: CompilerEvent): Unit =
      project.getMessageBus
        .syncPublisher(CompilerEventListener.topic)
        .eventReceived(event)
  }
}
