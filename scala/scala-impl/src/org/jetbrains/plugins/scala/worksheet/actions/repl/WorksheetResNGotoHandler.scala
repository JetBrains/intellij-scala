package org.jetbrains.plugins.scala.worksheet.actions.repl

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.OptionExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression

final class WorksheetResNGotoHandler extends GotoDeclarationHandler {

  override def getGotoDeclarationTargets(sourceElement: PsiElement, offset: Int, editor: Editor): Array[PsiElement] =
    if (sourceElement == null) null else {
      val referenced = WorksheetResNGotoHandler.findReferencedPsi(sourceElement.getParent)
      referenced.map(Array(_)).orNull
    }

  override def getActionText(context: DataContext): String = null
}

private object WorksheetResNGotoHandler {

  private val WorksheetGotoPsiKey = new Key[PsiElement]("WORKSHEET_GOTO_PSI_KEY")

  def findReferencedPsi(psiElement: PsiElement): Option[PsiElement] =
    for {
      ref   <- Option(psiElement).filterByType[ScReferenceExpression]
      expr  <- Option(ref.resolve())
      value <- Option(expr.getUserData(WorksheetResNGotoHandler.WorksheetGotoPsiKey))
      if value.isValid
    } yield value
}
