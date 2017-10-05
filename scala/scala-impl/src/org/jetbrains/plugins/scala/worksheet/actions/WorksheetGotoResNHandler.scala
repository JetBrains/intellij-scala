package org.jetbrains.plugins.scala.worksheet.actions

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression

/**
  * User: Dmitry.Naydanov
  * Date: 30.03.17.
  */
class WorksheetGotoResNHandler extends GotoDeclarationHandler {
  override def getGotoDeclarationTargets(sourceElement: PsiElement, offset: Int, editor: Editor): Array[PsiElement] = {
    if (sourceElement == null) null else WorksheetGotoResNHandler.findReferencedPsi(sourceElement.getParent).map(Array(_)).orNull
  }

  override def getActionText(context: DataContext): String = null
}

object WorksheetGotoResNHandler {
  val WORKSHEET_GOTO_PSI_KEY = new Key[PsiElement]("WORKSHEET_GOTO_PSI_KEY")
  
  def findReferencedPsi(psiElement: PsiElement): Option[PsiElement] = psiElement match {
    case refExpr: ScReferenceExpression =>
      Option(refExpr.resolve()) flatMap (
        expr => 
          Option(expr.getUserData(WorksheetGotoResNHandler.WORKSHEET_GOTO_PSI_KEY)).filter(_.isValid)
      )
    case _ => None
  }
}
