package org.jetbrains.plugins.scala
package codeInsight.intention.expression

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil.getParentOfType
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.util.IntentionUtils.replaceWithExplicit

/**
  * @author Ksenia.Sautina
  * @since 5/4/12
  */

object InlineImplicitConversionIntention {
  def familyName = "Provide implicit conversion"
}

class InlineImplicitConversionIntention extends PsiElementBaseIntentionAction {
  def getFamilyName: String = InlineImplicitConversionIntention.familyName

  override def getText: String = getFamilyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    parent(element).map {
      findConversions
    }.exists {
      case (maybeFunction, _) => maybeFunction.nonEmpty
    }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit =
    parent(element).foreach { expression =>
      findConversions(expression) match {
        case (Some(function: ScFunction), conversions) =>
          replaceWithExplicit(expression, function, project, editor, conversions)
        case _ =>
      }
    }

  private def parent(element: PsiElement) =
    Option(getParentOfType(element, classOf[ScExpression], false)).filter {
      _.isValid
    }

  private def findConversions(expression: ScExpression) = {
    val (_, maybeFunction, _, conversions) = expression.getImplicitConversions(fromUnderscore = true)
    (maybeFunction, conversions)
  }
}
