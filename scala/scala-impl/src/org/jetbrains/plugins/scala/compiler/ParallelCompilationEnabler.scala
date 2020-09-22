package org.jetbrains.plugins.scala.compiler

import java.util.UUID

import com.intellij.compiler.CompilerWorkspaceConfiguration
import com.intellij.compiler.server.BuildManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.{CompileContext, CompileTask}
import com.intellij.openapi.components.{Service, ServiceManager}
import com.intellij.openapi.project.Project

/**
 * #SCL-17704
 *
 * Temporary sets "Compile independent modules in parallel" value and then resets it.
 */
class ParallelCompilationEnabler
  extends CompileTask
    with BuildManagerListener {
  
  private def tempService(project: Project): TempParallelCompilationService =
    TempParallelCompilationService.get(project)
  
  // BEFORE
  override def execute(context: CompileContext): Boolean = {
    val settings = ScalaCompileServerSettings.getInstance
    if (settings.COMPILE_SERVER_ENABLED)
      tempService(context.getProject).setTempParallelCompilation(settings.COMPILE_SERVER_PARALLEL_COMPILATION)
    true
  }

  override def buildStarted(project: Project, sessionId: UUID, isAutomake: Boolean): Unit =
    tempService(project).resetParallelCompilation()

  override def buildFinished(project: Project, sessionId: UUID, isAutomake: Boolean): Unit =
    tempService(project).resetParallelCompilation()
}

@Service
final class TempParallelCompilationService(project: Project)
  extends Disposable {
  
  @volatile private var originalParallelCompilation: Option[Boolean] = None
  private val lock = new Object
  
  private def settings: CompilerWorkspaceConfiguration =
    CompilerWorkspaceConfiguration.getInstance(project)
  
  def setTempParallelCompilation(value: Boolean): Unit = lock.synchronized {
    if (originalParallelCompilation.isEmpty)
      originalParallelCompilation = Some(settings.PARALLEL_COMPILATION)
    settings.PARALLEL_COMPILATION = value
  }
  
  def resetParallelCompilation(): Unit = lock.synchronized {
    if (!originalParallelCompilation.contains(settings.PARALLEL_COMPILATION)) {
      originalParallelCompilation.foreach { originalValue =>
        settings.PARALLEL_COMPILATION = originalValue
        ApplicationManager.getApplication.saveSettings()
      }
    }
    originalParallelCompilation = None
  }
  
  override def dispose(): Unit =
    resetParallelCompilation()
}

object TempParallelCompilationService {
  
  def get(project: Project): TempParallelCompilationService =
    ServiceManager.getService(project, classOf[TempParallelCompilationService])
}
