package org.jetbrains.plugins.scala.codeInsight.intention.argument

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScArgumentExprList
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import java.lang.String
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

/**
 * Pavel Fatin
 */

class ArgumentToBlockExpressionIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = "Convert to block expression"

  override def getText: String = getFamilyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement) = {
    IntentionAvailabilityChecker.check(this, element) && (element match {
      case Parent(list: ScArgumentExprList) if list.exprs.size == 1 => true
      case _ => false
    })
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val list = element.getParent.asInstanceOf[ScArgumentExprList]
    val exp = list.exprs.head
    val block = ScalaPsiElementFactory.createBlockFromExpr(exp, list.getManager)
    exp.replace(block)
    list.getFirstChild.delete()
    list.getLastChild.delete()
  }
}