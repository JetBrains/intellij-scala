package org.jetbrains.plugins.scala.codeInsight.intention.argument

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScBlockExpr}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

/**
 * Pavel Fatin
 */

class BlockExpressionToArgumentIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = "Convert to argument in parentheses"

  override def getText: String = getFamilyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    element match {
      case Both(Parent(block: ScBlockExpr), Parent(Parent(list: ScArgumentExprList)))
        if list.exprs.size == 1 && block.caseClauses.isEmpty => IntentionAvailabilityChecker.checkIntention(this, element)
      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val block = element.getParent.asInstanceOf[ScBlockExpr]
    val s = block.getText
    val text = "foo(%s)".format(s.substring(1, s.length - 1).replaceAll("\n", ""))
    val arguments = createExpressionFromText(text)(block.getManager)
            .children.findByType[ScArgumentExprList].get
    val replacement = block.getParent.replace(arguments)
    replacement.getPrevSibling match {
      case ws: PsiWhiteSpace => ws.delete()
      case _ =>
    }
  }
}