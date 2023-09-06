package org.jetbrains.plugins.scala.codeInsight.intention.booleans

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.codeInsight.intention.booleans.FlipComparisonInMethodCallExprIntention.{Replacement, createFlippedCall}
import org.jetbrains.plugins.scala.codeInsight.intention.caretIsInRange
import org.jetbrains.plugins.scala.extensions.ParenthesizedElement.Ops
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.project.{ProjectContext, ScalaFeatures}

import scala.util.chaining.scalaUtilChainingOps

final class FlipComparisonInMethodCallExprIntention extends PsiElementBaseIntentionAction {

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val methodCallExpr: ScMethodCall = PsiTreeUtil.getParentOfType(element, classOf[ScMethodCall], false)
    if (methodCallExpr == null || methodCallExpr.args.exprs.sizeIs != 1) return false

    val operation = methodCallExpr.getInvokedExpr match {
      case ref: ScReferenceExpression => ref
      case _ => return false
    }
    val refName = operation.refName

    Replacement.get(refName) match {
      case Some(replacement) if caretIsInRange(operation)(editor) =>
        val text =
          if (replacement == refName) ScalaCodeInsightBundle.message("flip.operation", refName)
          else ScalaCodeInsightBundle.message("flip.operation.to.inverse", refName, replacement)
        setText(text)
        operation.isQualified
      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val methodCallExpr: ScMethodCall = PsiTreeUtil.getParentOfType(element, classOf[ScMethodCall], false)
    if (methodCallExpr == null || !methodCallExpr.isValid) return

    val (operation, qualifier) = methodCallExpr.getInvokedExpr match {
      case ref@ScReferenceExpression.withQualifier(qualifier) => (ref, qualifier)
      case _ => return
    }
    val argumentExpr = methodCallExpr.args match {
      case ScArgumentExprList(expr) => expr
      case _ => return
    }

    val start = methodCallExpr.getTextRange.getStartOffset
    val diff = editor.getCaretModel.getOffset - operation.nameId.getTextRange.getStartOffset

    import methodCallExpr.projectContext
    val flipped = createFlippedCall(qualifier.getText, operation, argumentExpr)(element)

    val size = flipped.getInvokedExpr.asInstanceOf[ScReferenceExpression].nameId.
      getTextRange.getStartOffset - flipped.getTextRange.getStartOffset

    IntentionPreviewUtils.write { () =>
      methodCallExpr.replaceExpression(flipped, removeParenthesis = true)
      editor.getCaretModel.moveToOffset(start + diff + size)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }

  override def getFamilyName: String = ScalaCodeInsightBundle.message("family.name.flip.comparison.in.method.call.expression")

  override def getText: String = getFamilyName
}

object FlipComparisonInMethodCallExprIntention {
  private val Replacement = Map(
    "equals" -> "equals",
    "==" -> "==",
    "!=" -> "!=",
    "eq" -> "eq",
    "ne" -> "ne",
    ">" -> "<",
    "<" -> ">",
    ">=" -> "<=",
    "<=" -> ">="
  )

  private def createFlippedCall(qualifier: String, operation: ScReferenceExpression, argument: ScExpression)
                               (features: ScalaFeatures)
                               (implicit ctx: ProjectContext): ScMethodCall = {
    val adjustedArgument = argument match {
      case block: ScBlockExpr if block.isEnclosedByColon =>
        val statements = block.statements
        if (statements.sizeIs == 1) statements.head
        else ScalaPsiUtil.convertBlockToBraced(block)
      case _ => argument
    }

    createExpressionFromText(s"(${adjustedArgument.getText}).${Replacement(operation.refName)}($qualifier)", features)
      .asInstanceOf[ScMethodCall]
      .tap { call =>
        call.thisExpr.foreach(stripUnnecessaryParentheses)
        call.args.exprs.foreach(stripUnnecessaryParentheses)
      }
  }

  private[this] def stripUnnecessaryParentheses(expr: ScExpression): Unit = expr match {
    case e: ScParenthesisedExpr if e.isParenthesisRedundant =>
      // if there already were parentheses in the expression before refactoring,
      // then keep one pair when stripping unnecessary parentheses
      e.doStripParentheses(keepOnePair = e.isNestingParenthesis)
    case _ =>
  }
}
