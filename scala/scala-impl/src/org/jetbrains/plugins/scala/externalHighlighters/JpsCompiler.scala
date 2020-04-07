package org.jetbrains.plugins.scala.externalHighlighters

import java.io.File

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.application.{ApplicationManager, PathManager}
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project
import com.intellij.util.io.PathKt
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.jps.incremental.scala.remote.{CommandIds, CompileServerCommand}
import org.jetbrains.jps.incremental.scala.{Client, DummyClient}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.compiler.{CompileServerLauncher, CompilerEvent, CompilerEventListener, CompilerLock, RemoteServerRunner}
import org.jetbrains.plugins.scala.util.CompilationId

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}
import scala.util.Try

trait JpsCompiler {
  def compile(): Unit
}

object JpsCompiler {

  def get(project: Project): JpsCompiler =
    ServiceManager.getService(project, classOf[JpsCompiler])
}

private class JpsCompilerImpl(project: Project)
  extends JpsCompiler {

  import JpsCompilerImpl.CompilationClient

  override def compile(): Unit = {
    CompileServerLauncher.ensureServerRunning(project)
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
    val promise = Promise[Unit]
    val taskMsg = ScalaBundle.message("highlighting.compilation")
    val task: Task = new Task.Backgroundable(project, taskMsg, false) {
      override def run(indicator: ProgressIndicator): Unit = CompilerLock.get(project).withLock {
        val client = new CompilationClient(project, indicator)
        val result = Try(new RemoteServerRunner(project).buildProcess(CommandIds.CompileJps, args, client).runSync())
        promise.complete(result)
      }
      override val isHeadless: Boolean = !ApplicationManager.getApplication.isInternal
    }
    ProgressManager.getInstance.run(task)
    Await.result(promise.future, Duration.Inf)
  }
}

private object JpsCompilerImpl {

  private class CompilationClient(project: Project, indicator: ProgressIndicator)
    extends DummyClient {

    final val compilationId = CompilationId.generate()

    indicator.setIndeterminate(false)

    override def progress(text: String, done: Option[Float]): Unit = {
      indicator.setText(ScalaBundle.message("highlighting.compilation.progress", text))
      indicator.setFraction(done.getOrElse(-1.0F).toDouble)
      done.foreach { doneVal =>
        sendEvent(CompilerEvent.ProgressEmitted(compilationId, doneVal))
      }
    }

    override def message(msg: Client.ClientMsg): Unit =
      sendEvent(CompilerEvent.MessageEmitted(compilationId, msg))

    override def compilationEnd(sources: Set[File]): Unit =
      sources.foreach { source =>
        sendEvent(CompilerEvent.CompilationFinished(compilationId, source))
      }

    private def sendEvent(event: CompilerEvent): Unit =
      project.getMessageBus
        .syncPublisher(CompilerEventListener.topic)
        .eventReceived(event)
  }
}
