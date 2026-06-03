package org.jetbrains.plugins.scala.compiler.charts

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

object CompilationProgressStateManager {

  def get(project: Project): CompilationProgressState =
    mutableState(project).state

  def update(project: Project, newState: CompilationProgressState): CompilationProgressState = {
    mutableState(project).state = newState
    newState
  }

  def erase(project: Project): CompilationProgressState =
    update(project, CompilationProgressState.Empty)

  private def mutableState(project: Project): MutableState =
    project.getService(classOf[MutableState])

  @Service
  private final class MutableState {
    var state: CompilationProgressState = CompilationProgressState.Empty
  }
}
