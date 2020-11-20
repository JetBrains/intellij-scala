package org.jetbrains.plugins.scala.compilationCharts

import com.intellij.openapi.components.{Service, ServiceManager}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.CompilationUnitId

object CompilationProgressStateManager {

  def get(project: Project): CompilationProgressState =
    mutableState(project).state

  def update(project: Project, newState: CompilationProgressState): CompilationProgressState = {
    mutableState(project).state = newState
    newState
  }

  def erase(project: Project): CompilationProgressState =
    update(project, Map.empty)

  private def mutableState(project: Project): MutableState =
    ServiceManager.getService(project, classOf[MutableState])

  @Service
  private final class MutableState {
    var state: CompilationProgressState = Map.empty
  }
}
