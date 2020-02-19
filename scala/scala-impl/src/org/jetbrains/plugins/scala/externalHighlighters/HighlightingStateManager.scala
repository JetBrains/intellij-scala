package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.{EditorFactoryEvent, EditorFactoryListener}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.WolfTheProblemSolver
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode
import org.jetbrains.plugins.scala.settings.ProblemSolverUtils
import org.jetbrains.plugins.scala.util.CompilationId

object HighlightingStateManager {

  case class FileHighlightingState(compilationId: CompilationId, highlightings: Set[ExternalHighlighting])

  type HighlightingState = Map[VirtualFile, FileHighlightingState]

  def get(project: Project): HighlightingState =
    mutableState(project).state

  def update(project: Project, newState: HighlightingState): HighlightingState = {
    mutableState(project).state = newState
    applyHighlightingState(project, newState)
    newState
  }

  private def mutableState(project: Project): MutableState =
    ServiceManager.getService(project, classOf[MutableState])

  private class MutableState(project: Project) {
    var state: HighlightingState = Map.empty

    EditorFactory.getInstance.addEditorFactoryListener(new EditorFactoryListener {
      override def editorCreated(event: EditorFactoryEvent): Unit = {
        val editor = event.getEditor
        ExternalHighlighters.applyHighlighting(project, editor, state)
      }
    }, project)
  }

  private def applyHighlightingState(project: Project, state: HighlightingState): Unit =
    if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) {
      ExternalHighlighters.updateOpenEditors(project, state)
      informWolf(project, state)
    }

  private def informWolf(project: Project, state: HighlightingState): Unit = {
    ProblemSolverUtils.clearAllProblemsFromExternalSource(project, this)
    val wolf = WolfTheProblemSolver.getInstance(project)
    val errorFiles = state.collect {
      case (file, fileState) if fileState.highlightings.exists(_.severity == HighlightSeverity.ERROR) => file
    }
    errorFiles.foreach(wolf.reportProblemsFromExternalSource(_, this))
  }
}
