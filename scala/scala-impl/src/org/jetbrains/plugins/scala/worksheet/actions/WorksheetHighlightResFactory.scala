package org.jetbrains.plugins.scala.worksheet.actions

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.highlighting.{HighlightUsagesHandlerBase, HighlightUsagesHandlerFactory}
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings

/**
  * User: Dmitry.Naydanov
  * Date: 30.03.17.
  */
class WorksheetHighlightResFactory extends HighlightUsagesHandlerFactory {
  override def createHighlightUsagesHandler(editor: Editor, file: PsiFile): HighlightUsagesHandlerBase[_ <: PsiElement] = {
    file match {
      case scalaFile: ScalaFile if scalaFile.isWorksheetFile && WorksheetFileSettings.isRepl(scalaFile) =>
        val offset = TargetElementUtil.adjustOffset(file, editor.getDocument, editor.getCaretModel.getOffset)
        val psi = file findElementAt offset

        if (psi != null && psi.getText.startsWith("res") && psi.getParent.isInstanceOf[ScReferenceExpression]) 
          WorksheetGotoResNHandler.findReferencedPsi(psi.getParent).map(
            referenced => new WorksheetHighlightResDeclarationHandler(editor, file, psi, referenced)
          ).orNull else null
      case _ => null
    }
  }
}
