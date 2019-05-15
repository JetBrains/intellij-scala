package org.jetbrains.plugins.scala
package codeInsight
package intention
package argument

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.codeInsight.intention.argument.BlockExpressionToArgumentIntention.FAMILY_NAME
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameters
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

/**
 * Pavel Fatin
 */
final class BlockExpressionToArgumentIntention extends PsiElementBaseIntentionAction {
  override def getFamilyName: String = FAMILY_NAME

  override def getText: String = getFamilyName

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    element match {
      case Parent((block: ScBlockExpr) && Parent(list: ScArgumentExprList))
        if list.exprs.size == 1 && block.caseClauses.isEmpty =>
        IntentionAvailabilityChecker.checkIntention(this, element) && singleFunctionExpressionStatement(block).isDefined
      case _ => false
    }

  private def singleFunctionExpressionStatement(block: ScBlock): Option[ScFunctionExpr] = {
    block.statements.toList match {
      case (funExpr: ScFunctionExpr) :: Nil => Some(funExpr)
      case _ => None
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val block = element.getParent.asInstanceOf[ScBlockExpr]
    val function: ScFunctionExpr = singleFunctionExpressionStatement(block).getOrElse(return)

    val params: ScParameters = function.params
    val body: ScExpression = function.result.getOrElse(return)

    val newParamsText: String = {
      val hasSomeType = function.parameters.exists(_.paramType.isDefined)
      val newParentheses = hasSomeType && !params.startsWithToken(ScalaTokenTypes.tLPARENTHESIS)
      params.getText.parenthesize(newParentheses)
    }

    val newBodyText: String = {
      val bodyText = body.getText
      val needBraces = bodyText.contains('\n') && !body.startsWithToken(ScalaTokenTypes.tLBRACE)
      bodyText.braced(needBraces)
    }

    val text = s"foo($newParamsText => $newBodyText)"
    val expression = createExpressionFromText(text)(block.getManager)
    val arguments = expression.children.instanceOf[ScArgumentExprList].getOrElse(return)
    val replacement = block.getParent.replace(arguments)
    replacement.getPrevSibling match {
      case ws: PsiWhiteSpace => ws.delete()
      case _ =>
    }
  }

}

object BlockExpressionToArgumentIntention {
  val FAMILY_NAME = "Convert to argument in parentheses"
}