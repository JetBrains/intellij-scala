package org.jetbrains.plugins.scala
package codeInsight
package intention
package booleans

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.extensions.ParenthesizedElement.Ops
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.project.ScalaFeatures

import scala.util.chaining.scalaUtilChainingOps

final class FlipComparisonInInfixExprIntention extends PsiElementBaseIntentionAction {

  import FlipComparisonInInfixExprIntention._

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val infixExpr = PsiTreeUtil.getParentOfType(element, classOf[ScInfixExpr], false)
    if (infixExpr == null) return false

    val operation = infixExpr.operation
    val refName = operation.refName

    Replacement.get(refName) match {
      case Some(replacement) if caretIsInRange(operation)(editor) =>
        val text =
          if (replacement == refName) ScalaCodeInsightBundle.message("flip.operation", refName)
          else ScalaCodeInsightBundle.message("flip.operation.to.inverse", refName, replacement)
        setText(text)
        true
      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val infixExpr = PsiTreeUtil.getParentOfType(element, classOf[ScInfixExpr], false)
    if (infixExpr == null || !infixExpr.isValid) return

    val ScInfixExpr.withAssoc(ElementText(baseText), operation, ElementText(argumentText)) = infixExpr

    val start = infixExpr.getTextRange.getStartOffset
    val diff = editor.getCaretModel.getOffset - operation.nameId.getTextRange.getStartOffset

    implicit val ctx: Project = project
    val newInfixExpr = createFlippedInfixExpr(baseText, operation, argumentText)(element)

    val size = newInfixExpr.operation.nameId.getTextRange.getStartOffset -
      newInfixExpr.getTextRange.getStartOffset

    IntentionPreviewUtils.write { () =>
      infixExpr.replace(newInfixExpr)
      editor.getCaretModel.moveToOffset(start + diff + size)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }

  override def getFamilyName: String = ScalaCodeInsightBundle.message("family.name.flip.comparison.in.infix.expression")
}

object FlipComparisonInInfixExprIntention {
  private val Replacement = Map(
    "equals" -> "equals",
    "==" -> "==",
    "!=" -> "!=",
    "eq" -> "eq",
    "ne" -> "ne",
    ">" -> "<",
    "<" -> ">",
    ">=" -> "<=",
    "<=" -> ">=",
    "&&" -> "&&",
    "||" -> "||"
  )

  private def createFlippedInfixExpr(baseText: String, operation: ScReferenceExpression, argumentText: String)
                                    (features: ScalaFeatures)
                                    (implicit ctx: Project): ScInfixExpr =
    createExpressionFromText(s"($argumentText) ${Replacement(operation.refName)} ($baseText)", features)
      .asInstanceOf[ScInfixExpr]
      .tap { infix =>
        stripUnnecessaryParentheses(infix.left)
        stripUnnecessaryParentheses(infix.right)
      }

  private[this] def stripUnnecessaryParentheses(expr: ScExpression): Unit = expr match {
    case e: ScParenthesisedExpr if e.isParenthesisRedundant =>
      // if there already were parentheses in the expression before refactoring,
      // then keep one pair when stripping unnecessary parentheses
      e.doStripParentheses(keepOnePair = e.isNestingParenthesis)
    case _ =>
  }
}
