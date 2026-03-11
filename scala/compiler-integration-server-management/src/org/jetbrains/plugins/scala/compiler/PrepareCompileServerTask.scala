package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.util.compile.ScalaCompileTask

private final class PrepareCompileServerTask extends ScalaCompileTask {
  import PrepareCompileServerTask.Log

  override protected def run(context: CompileContext): Boolean = {
    val project = context.getProject
    ensureCompileServerRunning(project)
    true
  }

  override protected def presentableName: String =
    "Starting Scala Compile Server"

  override protected def log: Logger = Log

  private def ensureCompileServerRunning(project: Project): Unit = {
    if (project.isDisposed) return
    val settings = ScalaCompileServerSettings.getInstance

    val compileServerRequired = settings.COMPILE_SERVER_ENABLED && project.hasScala
    Log.debug(s"CompileServerBuildManagerListener.compileServerRequired: $compileServerRequired")
    if (compileServerRequired) {
      CompileServerLauncher.ensureServerRunning(project)
    }
    if (ScalaCompileServerSettings.getInstance().COMPILE_SERVER_ENABLED)
      CompileServerNotificationsService.get(project).warnIfCompileServerJdkMayLeadToCompilationProblems()
  }
}

private object PrepareCompileServerTask {
  val Log: Logger = Logger.getInstance(classOf[PrepareCompileServerTask])
}
