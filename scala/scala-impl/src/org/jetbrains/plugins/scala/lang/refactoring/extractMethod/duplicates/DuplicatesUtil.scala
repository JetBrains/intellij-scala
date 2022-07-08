package org.jetbrains.plugins.scala.lang.refactoring.extractMethod.duplicates

import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.find.FindManager
import com.intellij.openapi.application.{ApplicationManager, ApplicationNamesInfo}
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.{Editor, FoldRegion, LogicalPosition, ScrollType}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiComment, PsiElement, PsiWhiteSpace}
import com.intellij.refactoring.RefactoringBundle
import com.intellij.ui.ReplacePromptDialog
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.{ScalaExtractMethodSettings, ScalaExtractMethodUtils}

import java.util

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
      case (_: ScReferenceExpression, _: ScExpression) => true
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

  def findDuplicates(settings: ScalaExtractMethodSettings): Seq[DuplicateMatch] = {
    val pattern = new DuplicatePattern(filtered(settings.elements.toSeq), settings.parameters.toSeq)(settings.projectContext)
    pattern.findDuplicates(settings.nextSibling.getParent)
  }

  def previewDuplicate(project: Project, editor: Editor, duplicate: DuplicateMatch)(work: => Unit): Unit = {
    val highlighter = new util.ArrayList[RangeHighlighter](1)
    highlightDuplicate(project, editor, duplicate, highlighter)
    val range = duplicate.textRange
    val logicalPosition: LogicalPosition = editor.offsetToLogicalPosition(range.getStartOffset)
    expandAllRegionsCoveringRange(project, editor, range)
    editor.getScrollingModel.scrollTo(logicalPosition, ScrollType.MAKE_VISIBLE)

    work

    HighlightManager.getInstance(project).removeSegmentHighlighter(editor, highlighter.get(0))
  }

  private def invokeDuplicateProcessing(duplicates: Seq[DuplicateMatch], settings: ScalaExtractMethodSettings)
                                       (implicit project: Project, editor: Editor): Unit = {
    var replaceAll = false
    var cancelled = false
    for ((d, idx) <- duplicates.zipWithIndex) {
      if (!replaceAll) {
        previewDuplicate(project, editor, d) {
          val dialog = showPromptDialog(settings.methodName, idx + 1, duplicates.size, project)
          dialog.getExitCode match {
            case FindManager.PromptResult.ALL =>
              replaceDuplicate(settings, d)
              replaceAll = true
            case FindManager.PromptResult.OK => replaceDuplicate(settings, d)
            case FindManager.PromptResult.SKIP =>
            case FindManager.PromptResult.CANCEL => cancelled = true
          }
        }

        if (cancelled) return
      }
      else replaceDuplicate(settings, d)
    }
  }

  private def replaceDuplicate(settings: ScalaExtractMethodSettings, d: DuplicateMatch)
                              (implicit project: Project): Unit =
    inWriteCommandAction {
      ScalaExtractMethodUtils.replaceWithMethodCall(settings, d)
    }

  private def showPromptDialog(methodName: String, idx: Int, size: Int, project: Project) = {
    val title = RefactoringBundle.message("process.methods.duplicates.title", Int.box(idx), Int.box(size), methodName)
    val dialog: ReplacePromptDialog = new ReplacePromptDialog(false, title, project)
    dialog.show()
    dialog
  }

  def processDuplicates(duplicates: Seq[DuplicateMatch], settings: ScalaExtractMethodSettings)
                       (implicit project: Project, editor: Editor): Unit = {
    def showDuplicatesDialog(): Int = {
      val message = RefactoringBundle.message("0.has.detected.1.code.fragments.in.this.file.that.can.be.replaced.with.a.call.to.extracted.method",
        ApplicationNamesInfo.getInstance.getProductName, Int.box(duplicates.size))
      Messages.showYesNoDialog(project, message, ScalaBundle.message("process.duplicates"), Messages.getQuestionIcon)
    }

    if (ApplicationManager.getApplication.isUnitTestMode) {
      duplicates.foreach(replaceDuplicate(settings, _))
      return
    }

    if (duplicates.size == 1) {
      val duplicate = duplicates.head
      previewDuplicate(project, editor, duplicate) {
        if (showDuplicatesDialog() == Messages.YES) replaceDuplicate(settings, duplicate)
      }
      return
    }

    if (showDuplicatesDialog() == Messages.YES) {
      invokeDuplicateProcessing(duplicates, settings)
    }
  }

  private def expandAllRegionsCoveringRange(project: Project, editor: Editor, textRange: TextRange): Unit = {
    val foldRegions: Array[FoldRegion] = CodeFoldingManager.getInstance(project).getFoldRegionsAtOffset(editor, textRange.getStartOffset)
    val anyCollapsed: Boolean = foldRegions.exists(!_.isExpanded)
    if (anyCollapsed) {
      editor.getFoldingModel.runBatchFoldingOperation(() => foldRegions.filterNot(_.isExpanded).foreach(_.setExpanded(true))
      )
    }
  }

  def highlightDuplicate(project: Project, editor: Editor, duplicate: DuplicateMatch, highlighters: util.Collection[RangeHighlighter]): Unit = {
    val attributes = EditorColors.SEARCH_RESULT_ATTRIBUTES
    val range = duplicate.textRange
    HighlightManager.getInstance(project).addRangeHighlight(
      editor, range.getStartOffset, range.getEndOffset, attributes, true, highlighters
    )
  }

}
