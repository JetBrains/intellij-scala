package org.jetbrains.plugins.scala.worksheet.actions.repl

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.highlighting.{HighlightUsagesHandlerBase, HighlightUsagesHandlerFactory}
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings

final class WorksheetResNHighlightFactory extends HighlightUsagesHandlerFactory {

  override def createHighlightUsagesHandler(editor: Editor, file: PsiFile): HighlightUsagesHandlerBase[PsiElement] =
    file match {
      case scalaFile: ScalaFile if scalaFile.isWorksheetFile && WorksheetFileSettings.isRepl(scalaFile) =>
        doCreateHighlightUsagesHandler(editor, file)
      case _ =>
        null
    }

  private def doCreateHighlightUsagesHandler(editor: Editor, file: PsiFile): WorksheetResNHighlightHandler = {
    val offset = TargetElementUtil.adjustOffset(file, editor.getDocument, editor.getCaretModel.getOffset)
    val psi = file.findElementAt(offset)

    if (psi != null && isResNReference(psi)) {
      val referenced = WorksheetResNGotoHandler.findReferencedPsi(psi.getParent)
      val highlighter = referenced.map(new WorksheetResNHighlightHandler(editor, file, psi, _))
      highlighter.orNull
    } else {
      null
    }
  }

  @inline
  private def isResNReference(psi: PsiElement): Boolean =
    psi.getText.startsWith("res") && psi.getParent.isInstanceOf[ScReferenceExpression]
}
