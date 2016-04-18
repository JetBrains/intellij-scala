package org.jetbrains.plugins.scala.lang.refactoring.extractMethod.duplicates

import java.util

import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.find.FindManager
import com.intellij.openapi.application.{ApplicationManager, ApplicationNamesInfo}
import com.intellij.openapi.editor.colors.{EditorColors, EditorColorsManager}
import com.intellij.openapi.editor.markup.{RangeHighlighter, TextAttributes}
import com.intellij.openapi.editor.{Editor, FoldRegion, LogicalPosition, ScrollType}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiComment, PsiElement, PsiWhiteSpace}
import com.intellij.refactoring.RefactoringBundle
import com.intellij.ui.ReplacePromptDialog
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.{ScalaExtractMethodSettings, ScalaExtractMethodUtils}

/**
 * Nikolay.Tropin
 * 2014-05-15
 */
object DuplicatesUtil {
  def isSignificant(e: PsiElement): Boolean = e match {
    case _: PsiWhiteSpace => false
    case _: PsiComment => false
    case ElementType(tp) if tp == ScalaTokenTypes.tSEMICOLON => false
    case _ => true
  }

  def filtered(elements: Seq[PsiElement]): Seq[PsiElement] = {
    elements.filter(isSignificant)
  }

  def filteredChildren(element: PsiElement): Seq[PsiElement] = {
    filtered(element.children.toSeq)
  }

  def isUnder(element: PsiElement, parents: Seq[PsiElement]): Boolean = {
    parents.exists(PsiTreeUtil.isAncestor(_, element, false))
  }

  def canBeEquivalent(pattern: PsiElement, candidate: PsiElement): Boolean = {
    (pattern, candidate) match {
      case (ref: ScReferenceExpression, expr: ScExpression) => true
      case (ElementType(tp1), ElementType(tp2)) => tp1 == tp2
        //todo this expressions, return statements, infix expressions
      case _ => false
    }
  }

  def withFilteredForwardSiblings(element: PsiElement, size: Int): Option[Seq[PsiElement]] = {
    val siblingIterator = element.nextSiblings
    val siblings = element +: siblingIterator.withFilter(isSignificant).take(size - 1).toSeq
    if (siblings.size < size) None
    else Some(siblings)
  }

  def findDuplicates(settings: ScalaExtractMethodSettings)
                    (implicit typeSystem: TypeSystem): Seq[DuplicateMatch] = {
    val pattern = new DuplicatePattern(filtered(settings.elements), settings.parameters)
    pattern.findDuplicates(settings.nextSibling.getParent)
  }

  def previewDuplicate(project: Project, editor: Editor, duplicate: DuplicateMatch)(work: => Unit) {
    val highlighter = new util.ArrayList[RangeHighlighter](1)
    highlightDuplicate(project, editor, duplicate, highlighter)
    val range = duplicate.textRange
    val logicalPosition: LogicalPosition = editor.offsetToLogicalPosition(range.getStartOffset)
    expandAllRegionsCoveringRange(project, editor, range)
    editor.getScrollingModel.scrollTo(logicalPosition, ScrollType.MAKE_VISIBLE)

    work

    HighlightManager.getInstance(project).removeSegmentHighlighter(editor, highlighter.get(0))
  }

  private def invokeDuplicateProcessing(duplicates: Seq[DuplicateMatch], settings: ScalaExtractMethodSettings, project: Project, editor: Editor) {
    var replaceAll = false
    var cancelled = false
    for ((d, idx) <- duplicates.zipWithIndex) {
      if (!replaceAll) {
        previewDuplicate(project, editor, d) {
          val dialog = showPromptDialog(settings.methodName, idx + 1, duplicates.size, project)
          dialog.getExitCode match {
            case FindManager.PromptResult.ALL =>
              replaceDuplicate(project, settings, d)
              replaceAll = true
            case FindManager.PromptResult.OK => replaceDuplicate(project, settings, d)
            case FindManager.PromptResult.SKIP =>
            case FindManager.PromptResult.CANCEL => cancelled = true
          }
        }

        if (cancelled) return
      }
      else replaceDuplicate(project, settings, d)
    }
  }

  private def replaceDuplicate(project: Project, settings: ScalaExtractMethodSettings, d: DuplicateMatch) =
    inWriteCommandAction(project, "Replace duplicate") {
      ScalaExtractMethodUtils.replaceWithMethodCall(settings, d)
    }

  private def showPromptDialog(methodName: String, idx: Int, size: Int, project: Project) = {
    val title = RefactoringBundle.message("process.methods.duplicates.title", Int.box(idx), Int.box(size), methodName)
    val dialog: ReplacePromptDialog = new ReplacePromptDialog(false, title, project)
    dialog.show()
    dialog
  }

  def processDuplicates(duplicates: Seq[DuplicateMatch], settings: ScalaExtractMethodSettings, project: Project, editor: Editor) {
    def showDuplicatesDialog(): Int = {
      val message = RefactoringBundle.message("0.has.detected.1.code.fragments.in.this.file.that.can.be.replaced.with.a.call.to.extracted.method",
        ApplicationNamesInfo.getInstance.getProductName, Int.box(duplicates.size))
      Messages.showYesNoDialog(project, message, "Process Duplicates", Messages.getQuestionIcon)
    }

    if (ApplicationManager.getApplication.isUnitTestMode) {
      duplicates.foreach(replaceDuplicate(project, settings, _))
      return
    }

    if (duplicates.size == 1) {
      previewDuplicate(project, editor, duplicates(0)) {
        if (showDuplicatesDialog() == Messages.YES) replaceDuplicate(project, settings, duplicates(0))
      }
      return
    }

    if (showDuplicatesDialog() == Messages.YES) {
      invokeDuplicateProcessing(duplicates, settings, project, editor)
    }
  }

  private def expandAllRegionsCoveringRange(project: Project, editor: Editor, textRange: TextRange) {
    val foldRegions: Array[FoldRegion] = CodeFoldingManager.getInstance(project).getFoldRegionsAtOffset(editor, textRange.getStartOffset)
    val anyCollapsed: Boolean = foldRegions.exists(!_.isExpanded)
    if (anyCollapsed) {
      editor.getFoldingModel.runBatchFoldingOperation(new Runnable {
          def run() = foldRegions.filterNot(_.isExpanded).foreach(_.setExpanded(true))
        }
      )
    }
  }

  def highlightDuplicate(project: Project, editor: Editor, duplicate: DuplicateMatch, highlighters: util.Collection[RangeHighlighter]) {
    val colorsManager: EditorColorsManager = EditorColorsManager.getInstance
    val attributes: TextAttributes = colorsManager.getGlobalScheme.getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES)
    val range = duplicate.textRange
    HighlightManager.getInstance(project).addRangeHighlight(editor, range.getStartOffset, range.getEndOffset, attributes, true, highlighters)
  }

}
