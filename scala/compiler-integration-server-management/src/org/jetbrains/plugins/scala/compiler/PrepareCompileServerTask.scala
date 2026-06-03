package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.util.compile.ScalaCompileTask

private final class PrepareCompileServerTask extends ScalaCompileTask {
  import PrepareCompileServerTask.Log

  override protected def run(context: CompileContext): Boolean = {
    ensureCompileServerRunning(context)
    true
  }

  override protected def presentableName: String =
    "Starting Scala Compile Server"

  override protected def log: Logger = Log

  private def ensureCompileServerRunning(context: CompileContext): Unit = {
    val project = context.getProject
    if (project.isDisposed) return
    val settings = ScalaCompileServerSettings.getInstance

    val compileServerRequired = settings.COMPILE_SERVER_ENABLED && hasRelevantScalaModulesInCompileScope(context)
    Log.debug(s"CompileServerBuildManagerListener.compileServerRequired: $compileServerRequired")
    if (compileServerRequired) {
      CompileServerLauncher.ensureServerRunning(project)
    }
    if (ScalaCompileServerSettings.getInstance().COMPILE_SERVER_ENABLED)
      CompileServerNotificationsService.get(project).warnIfCompileServerJdkMayLeadToCompilationProblems()
  }

  private def hasRelevantScalaModulesInCompileScope(context: CompileContext): Boolean = {
    val affectedModules = Option(context.getCompileScope)
      .map(_.getAffectedModules)
      .getOrElse(Array.empty)

    affectedModules.exists { module =>
      module != null &&
        module.hasScala &&
        !module.isBuildModule
    }
  }
}

private object PrepareCompileServerTask {
  val Log: Logger = Logger.getInstance(classOf[PrepareCompileServerTask])
}
