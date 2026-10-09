package org.jetbrains.plugins.scala.compiler.highlighting.services.core

import com.intellij.codeInsight.daemon.impl.{ErrorStripeUpdateManager, UpdateHighlightersUtil}
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.editor.{Editor, EditorFactory}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.WolfTheProblemSolver
import org.jetbrains.plugins.scala.caches.ModTracker.anyScalaPsiChange
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHints
import org.jetbrains.plugins.scala.compiler.highlighting.services.ExternalHighlightersService.{AppliedHighlightings, HighlightInfoData, HighlightingData, ScalaCompilerPassId}
import org.jetbrains.plugins.scala.extensions.invokeLater
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.settings.{ProblemSolverUtils, ScalaHighlightingMode, ScalaProjectSettings}

import java.util.Collections
import scala.annotation.nowarn
import scala.jdk.CollectionConverters.*

/**
 * Applies highlighting data and diagnostics to the workspace interface.
 *
 * Responsible for interfacing with the editor subsystem to visually render highlights,
 * update error stripe panels, trigger semantic structural updates, and synchronize
 * errors with project-wide problem tracking services.
 */
private[highlighting] class ExternalHighlightingUpdater(project: Project, problemSource: AnyRef) {

  /**
   * Applies each file's highlightings to its editor, skipping a file that has been edited since they were built: their
   * offsets were computed against the text it had then.
   */
  def applyHighlightingInfo(highlightInfoData: HighlightInfoData): AppliedHighlightings = {
    val infos = highlightInfoData.highlightingData
    val expressions = highlightInfoData.psiElements
    val settings = ScalaProjectSettings.getInstance(project)

    def shouldApplyHighlightings(editor: Editor) = {
      // If autocomplete is in progress, apply only types but not errors (see CompilerTypeRequestListener)
      !(settings.isCompilerHighlightingScala3 && settings.isUseCompilerTypes) || LookupManager.getActiveLookup(editor) == null
    }

    if (!project.isDisposed) {
      val (current, stale) = infos.partition(data => data.document.getModificationStamp == data.textStamp)
      val applied = current.collect {
        case HighlightingData(editor, document, psiFile, virtualFile, highlightInfos, _) if shouldApplyHighlightings(editor) =>
          val collection = highlightInfos.asJavaCollection
          UpdateHighlightersUtil.setHighlightersToEditor(
            project,
            document, 0, document.getTextLength,
            collection,
            editor.getColorsScheme,
            ScalaCompilerPassId
          ): @nowarn("cat=deprecation")
          ErrorStripeUpdateManager.getInstance(project).launchRepaintErrorStripePanel(editor, psiFile)
          virtualFile
      }.toSet

      if (expressions.nonEmpty) {
        // We change the type of the expression without changing the PSI, so we trigger the update manually (see ScalaPsiChangeListener)
        expressions.foreach { e =>
          ScalaPsiManager.instance(project).clearOnScalaElementChange(e)
        }
        // Update usages (see ScalaFileImpl.subtreeChanged, Search.Method.getUsages)
        anyScalaPsiChange.incModificationCount()
        // Also update hints (type hints, implicit hints, x-ray mode, etc.)
        ImplicitHints.updateInAllEditors()
      }

      AppliedHighlightings(applied, stale.map(_.virtualFile).toSet)
    } else {
      // The project is disposed, so nothing was applied to the editors.
      AppliedHighlightings.empty
    }
  }

  def clearWolfProblems(file: VirtualFile, source: AnyRef): Unit = {
    WolfTheProblemSolver.getInstance(project).clearProblemsFromExternalSource(
      file, source
    )
  }
  def eraseEditorHighlightings(editor: Editor): Unit = {
    val document = editor.getDocument
    UpdateHighlightersUtil.setHighlightersToEditor(
      project,
      document,
      0,
      document.getTextLength,
      Collections.emptyList(),
      editor.getColorsScheme,
      ScalaCompilerPassId
    ): @nowarn("cat=deprecation")
  }
  def eraseAllHighlightings(): Unit = {
    for {
      editor <- EditorFactory.getInstance.getAllEditors
      editorProject <- Option(editor.getProject)
      if editorProject == project
    } invokeLater {
      if (!project.isDisposed) {
        eraseEditorHighlightings(editor)
      }
    }
    clearAllWolfProblems()
  }
  
  private def clearAllWolfProblems(): Unit =
    ProblemSolverUtils.clearAllProblemsFromExternalSource(project, problemSource)


  private[highlighting] def informWolf(errorFiles: Set[VirtualFile]): Unit = {
    if (!project.isDisposed && ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) {
      ProblemSolverUtils.clearAllProblemsFromExternalSource(project, problemSource)
      val wolf = WolfTheProblemSolver.getInstance(project)
      errorFiles.foreach(wolf.reportProblemsFromExternalSource(_, problemSource))
    }
  }
}
