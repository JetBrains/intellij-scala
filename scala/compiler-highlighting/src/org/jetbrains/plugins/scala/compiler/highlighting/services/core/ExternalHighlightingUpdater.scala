package org.jetbrains.plugins.scala.compiler.highlighting.services.core

import com.intellij.codeInsight.daemon.impl.{ErrorStripeUpdateManager, UpdateHighlightersUtil}
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.editor.{Editor, EditorFactory}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.WolfTheProblemSolver
import org.jetbrains.plugins.scala.caches.ModTracker.anyScalaPsiChange
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHints
import org.jetbrains.plugins.scala.compiler.highlighting.services.ExternalHighlightersService.{HighlightInfoData, HighlightingData, ScalaCompilerPassId}
import org.jetbrains.plugins.scala.compiler.highlighting.util.DocumentUtil
import org.jetbrains.plugins.scala.extensions.{executeOnPooledThread, invokeLater}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.settings.{ProblemSolverUtils, ScalaHighlightingMode, ScalaProjectSettings}
import org.jetbrains.plugins.scala.util.CompilationId

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

  def applyHighlightingInfo(highlightInfoData: HighlightInfoData, compilationId: CompilationId): Set[VirtualFile] = {
    val infos = highlightInfoData.highlightingData
    val errorFiles = highlightInfoData.virtualFiles
    val expressions = highlightInfoData.psiElements
    val settings = ScalaProjectSettings.getInstance(project)

    def shouldApplyHighlightings(editor: Editor) = {
      // If autocomplete is in progress, apply only types but not errors (see CompilerTypeRequestListener)
      !(settings.isCompilerHighlightingScala3 && settings.isUseCompilerTypes) || LookupManager.getActiveLookup(editor) == null
    }

    if (!project.isDisposed && DocumentUtil.stillValid(compilationId.documentVersions)) {
      val applied = infos.collect {
        case HighlightingData(editor, document, psiFile, virtualFile, highlightInfos) if shouldApplyHighlightings(editor) =>
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
      // Show red squiggly lines for errors in Project View.
      executeOnPooledThread(informWolf(errorFiles))

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

      applied
    } else {
      // The project is disposed or the results are stale, so nothing was applied to the editors.
      Set.empty[VirtualFile]
    }
  }

  def eraseAllHighlightings(): Unit = {
    for {
      editor <- EditorFactory.getInstance.getAllEditors
      editorProject <- Option(editor.getProject)
      if editorProject == project
    } invokeLater {
      if (!project.isDisposed) {
        val document = editor.getDocument
        UpdateHighlightersUtil.setHighlightersToEditor(
          project,
          document, 0, document.getTextLength,
          Collections.emptyList(),
          editor.getColorsScheme,
          ScalaCompilerPassId
        ): @nowarn("cat=deprecation")
      }
    }
    ProblemSolverUtils.clearAllProblemsFromExternalSource(project, problemSource)
  }

  private def informWolf(errorFiles: Set[VirtualFile]): Unit = {
    if (!project.isDisposed && ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) {
      ProblemSolverUtils.clearAllProblemsFromExternalSource(project, problemSource)
      val wolf = WolfTheProblemSolver.getInstance(project)
      errorFiles.foreach(wolf.reportProblemsFromExternalSource(_, problemSource))
    }
  }
}
