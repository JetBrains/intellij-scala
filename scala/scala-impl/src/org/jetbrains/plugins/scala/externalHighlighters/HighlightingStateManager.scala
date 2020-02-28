package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.util.CompilationId

object HighlightingStateManager {

  case class FileHighlightingState(compilationId: CompilationId, highlightings: Set[ExternalHighlighting])

  type HighlightingState = Map[VirtualFile, FileHighlightingState]

  def get(project: Project): HighlightingState =
    mutableState(project).state

  def update(project: Project, newState: HighlightingState): HighlightingState = {
    mutableState(project).state = newState
    ExternalHighlighters.updateOpenEditors(project, newState)
    ExternalHighlighters.informWolf(project, newState)
    newState
  }

  private def mutableState(project: Project): MutableState =
    ServiceManager.getService(project, classOf[MutableState])

  private class MutableState {
    var state: HighlightingState = Map.empty
  }
}
