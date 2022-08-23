package org.jetbrains.plugins.scala.worksheet.actions.repl

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.highlighting.{HighlightUsagesHandlerBase, HighlightUsagesHandlerFactory}
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.worksheet.WorksheetFile

final class WorksheetResNHighlightFactory extends HighlightUsagesHandlerFactory {

  override def createHighlightUsagesHandler(editor: Editor, file: PsiFile): HighlightUsagesHandlerBase[PsiElement] =
    file match {
      case file: WorksheetFile if ResNUtils.isResNSupportedInFile(file) =>
        doCreateHighlightUsagesHandler(editor, file)
      case _ =>
        null
    }

  private def doCreateHighlightUsagesHandler(editor: Editor, file: PsiFile): WorksheetResNHighlightHandler = {
    val offset = TargetElementUtil.adjustOffset(file, editor.getDocument, editor.getCaretModel.getOffset)
    val elementAtCaret = file.findElementAt(offset)

    if (elementAtCaret != null && isResNReference(elementAtCaret)) {
      val referenced = WorksheetResNGotoHandler.findReferencedPsi(elementAtCaret.getParent)
      val highlighter = referenced.map(new WorksheetResNHighlightHandler(editor, file, elementAtCaret, _))
      highlighter.orNull
    } else {
      null
    }
  }


  @inline
  private def isResNReference(psi: PsiElement): Boolean = {
    val text = psi.getText
    ResNUtils.ResNRegex.matches(text) && psi.getParent.isInstanceOf[ScReferenceExpression]
  }
}
