package org.jetbrains.plugins.scala.codeInsight.intention.expression

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInsight.intention.expression.ConvertToInfixExpressionIntention.{createInfix, qualifiedRef}
import org.jetbrains.plugins.scala.extensions.ParenthesizedElement.Ops
import org.jetbrains.plugins.scala.extensions.{ElementText, ObjectExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

final class ConvertToInfixExpressionIntention extends PsiElementBaseIntentionAction {
  override def getFamilyName: String = ScalaBundle.message("family.name.convert.to.infix.expression")

  override def getText: String = getFamilyName

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    if (!IntentionAvailabilityChecker.checkIntention(this, element)) return false
    val methodCallExpr: ScMethodCall = PsiTreeUtil.getParentOfType(element, classOf[ScMethodCall], false)
    if (methodCallExpr == null) return false
    val referenceExpr = qualifiedRef(methodCallExpr) match {
      case Some(ref) => ref
      case _ => return false
    }

    val range: TextRange = referenceExpr.nameId.getTextRange
    val offset = editor.getCaretModel.getOffset
    range.getStartOffset <= offset && offset <= range.getEndOffset
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val methodCallExpr: ScMethodCall = PsiTreeUtil.getParentOfType(element, classOf[ScMethodCall], false)
    if (methodCallExpr == null || !methodCallExpr.isValid) return

    val referenceExpr = qualifiedRef(methodCallExpr) match {
      case Some(ref) => ref
      case _ => return
    }

    val start = methodCallExpr.getTextRange.getStartOffset
    val diff = editor.getCaretModel.getOffset - referenceExpr.nameId.getTextRange.getStartOffset

    createInfix(methodCallExpr).foreach { infix =>
      val size = infix.operation.nameId.getTextRange.getStartOffset - infix.getTextRange.getStartOffset

      IntentionPreviewUtils.write { () =>
        methodCallExpr.replaceExpression(infix, removeParenthesis = true)
        editor.getCaretModel.moveToOffset(start + diff + size)
        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
      }
    }
  }
}

object ConvertToInfixExpressionIntention {
  private def qualifiedRef(call: ScMethodCall): Option[ScReferenceExpression] =
    call.getInvokedExpr match {
      case ref: ScReferenceExpression =>
        Option.when(ref.isQualified)(ref)
      case call: ScGenericCall => // if the expression has type args
        call.referencedExpr
          .asOptionOf[ScReferenceExpression]
          .filter(_.isQualified)
      case _ => None
    }

  private def createInfix(call: ScMethodCall): Option[ScInfixExpr] = {
    qualifiedRef(call) match {
      case Some(referenceExpr@ScReferenceExpression.withQualifier(qualifier)) =>
        import call.projectContext

        val qualifierText = qualifier.getText
        val refText = referenceExpr.nameId.getText

        val (operationText, argumentsFirst) = call.getInvokedExpr match {
          case call: ScGenericCall =>
            (refText + call.typeArgs.getText, false)
          case ElementText(text) =>
            (refText, text.endsWith(":"))
        }

        val argumentsText = (call.args match {
          case ScArgumentExprList(expr) =>
            expr match {
              case block: ScBlockExpr if block.isEnclosedByColon =>
                val statements = block.statements
                if (statements.sizeIs == 1) statements.head
                else ScalaPsiUtil.convertBlockToBraced(block)
              case _ => expr
            }
          case args => args
        }).getText

        val text =
          if (argumentsFirst)
            s"($argumentsText) $operationText ($qualifierText)"
          else
            s"($qualifierText) $operationText ($argumentsText)"

        createExpressionFromText(text, call)
          .asOptionOf[ScInfixExpr]
          .map { infix =>
            stripUnnecessaryParentheses(infix.left)
            stripUnnecessaryParentheses(infix.right)
            infix
          }
      case _ => None
    }
  }

  private[this] def stripUnnecessaryParentheses(expr: ScExpression): Unit = expr match {
    case e: ScParenthesisedExpr if e.isParenthesisRedundant =>
      e.doStripParentheses()
    case _ =>
  }
}
