package org.jetbrains.plugins.scala
package codeInsight
package intention
package argument

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScFunctionExpr, ScUnderscoreSection}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createAnonFunBlockFromFunExpr, createBlockFromExpr}
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

/**
 * Pavel Fatin
 */
final class ArgumentToBlockExpressionIntention extends PsiElementBaseIntentionAction {

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    IntentionAvailabilityChecker.checkIntention(this, element) && (element match {
      case Parent(list: ScArgumentExprList) if list.exprs.size == 1 && !list.exprs.head.isInstanceOf[ScUnderscoreSection] => true
      case _ => false
    })
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val list = element.getParent.asInstanceOf[ScArgumentExprList]
    val exp = list.exprs.head
    implicit val projectContext = list.projectContext
    val block = exp match {
      case funExpr: ScFunctionExpr => createAnonFunBlockFromFunExpr(funExpr)
      case _ => createBlockFromExpr(exp)
    }
    exp.replace(block)
    list.getFirstChild.delete()
    list.getLastChild.delete()
    CodeStyleManager.getInstance(project).reformat(block)
  }

  override def getFamilyName: String = ArgumentToBlockExpressionIntention.FamilyName

  override def getText: String = getFamilyName
}

object ArgumentToBlockExpressionIntention {

  private[argument] val FamilyName = "Convert to block expression"
}