package org.jetbrains.plugins.scala.codeInsight.intention.argument

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScFunctionExpr, ScUnderscoreSection}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

/**
 * Pavel Fatin
 */

class ArgumentToBlockExpressionIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = ArgumentToBlockExpressionIntention.familyName

  override def getText: String = getFamilyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement) = {
    IntentionAvailabilityChecker.checkIntention(this, element) && (element match {
      case Parent(list: ScArgumentExprList) if list.exprs.size == 1 && !list.exprs(0).isInstanceOf[ScUnderscoreSection] => true
      case _ => false
    })
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val list = element.getParent.asInstanceOf[ScArgumentExprList]
    val exp = list.exprs.head
    val block = exp match {
      case funExpr: ScFunctionExpr => ScalaPsiElementFactory.createAnonFunBlockFromFunExpr(funExpr, list.getManager)
      case _ => ScalaPsiElementFactory.createBlockFromExpr(exp, list.getManager)
    }
    exp.replace(block)
    list.getFirstChild.delete()
    list.getLastChild.delete()
    val manager: CodeStyleManager = CodeStyleManager.getInstance(project)
    manager.reformat(block)
  }
}

object ArgumentToBlockExpressionIntention {
  val familyName = "Convert to block expression"
}