package org.jetbrains.plugins.scala.codeInsight.intention.expression

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInsight.intention.expression.ConvertParameterToUnderscoreIntention._
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScOptionalBracesOwner
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionFromText, createNewLine}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.chaining.scalaUtilChainingOps

final class ConvertParameterToUnderscoreIntention extends PsiElementBaseIntentionAction {
  override def getFamilyName: String = ScalaBundle.message("family.name.convert.parameter.to.underscore.section")

  override def getText: String = getFamilyName

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    Option(PsiTreeUtil.getParentOfType(element, classOf[ScFunctionExpr], false)) match {
      case Some(expr: ScFunctionExpr) =>
        val range = expr.params.getTextRange
        val offset = editor.getCaretModel.getOffset
        range.getStartOffset <= offset && offset <= range.getEndOffset + 3
      case _ =>
        false
    }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit =
    Option(PsiTreeUtil.getParentOfType(element, classOf[ScFunctionExpr], false)) match {
      case Some(expr: ScFunctionExpr) if expr.isValid =>
        createExpressionToIntroduce(expr, withoutParameterTypes = false) match {
          case Right(newExpr) =>
            val added = expr.getParent match {
              case parent: ScOptionalBracesOwner if !expr.startsFromNewLine() && parent.isEnclosedByColon =>
                parent.addAfter(newExpr, expr).tap { _ =>
                  expr.replace(createNewLine()(project))
                }
              case _ => expr.replace(newExpr)
            }
            editor.getCaretModel.moveToOffset(added.getTextOffset)
          case Left(message) if !ApplicationManager.getApplication.isUnitTestMode =>
            //noinspection ReferencePassedToNls
            HintManager.getInstance().showErrorHint(editor, message)
          case _ =>
        }
      case _ =>
    }
}

object ConvertParameterToUnderscoreIntention {
  private def seekParams(fun: ScFunctionExpr): Map[String, Int] = {
    val map = new mutable.HashMap[String, Int]()
    var clearMap = false

    fun.accept(new ScalaRecursiveElementVisitor {
      override def visitReferenceExpression(expr: ScReferenceExpression): Unit = {
        expr.resolve() match {
          case p: ScParameter if fun.parameters.contains(p) =>
            if (!map.contains(expr.getText))
              map.put(expr.getText, expr.getTextRange.getStartOffset)
            else
              clearMap = true
          case _ =>
        }
        super.visitReferenceExpression(expr)
      }
    })

    if (clearMap) Map.empty else map.toMap
  }

  @tailrec
  private def isValidExpr(expr: ScExpression, paramCount: Int): Boolean =
    if (ScUnderScoreSectionUtil.underscores(expr).length == paramCount) true
    else expr match {
      case ScBlockExpr.Expressions(head) => isValidExpr(head, paramCount)
      case ScParenthesisedExpr(e) => isValidExpr(e, paramCount)
      case _ => false
    }

  private def isImplicitCorrectOrder(expr: ScFunctionExpr, occurrences: Map[String, Int]): Boolean = {
    var previousOffset = -1
    for (p <- expr.parameters) occurrences.get(p.name) match {
      case Some(offset) if offset >= previousOffset =>
        previousOffset = offset
      case _ =>
        return false
    }
    true
  }

  def createExpressionToIntroduce(expr: ScFunctionExpr, withoutParameterTypes: Boolean): Either[String, ScExpression] = expr.result match {
    case Some(result) =>
      val occurrences = seekParams(expr)
      if (occurrences.isEmpty || occurrences.size != expr.parameters.size)
        Left(ScalaInspectionBundle.message("introduce.implicit.incorrect.count"))
      else if (!isImplicitCorrectOrder(expr, occurrences))
        Left(ScalaInspectionBundle.message("introduce.implicit.incorrect.order"))
      else {
        val buf = new mutable.StringBuilder(result.getText)
        val diff = result.getTextRange.getStartOffset
        for (p <- expr.parameters.reverse) {
          val newParam =
            if (withoutParameterTypes) "_"
            else p.typeElement match {
              case Some(tpe) => s"(_: ${tpe.getText})"
              case None => "_"
            }
          val offset = occurrences(p.name) - diff
          buf.replace(offset, offset + p.name.length, newParam)
        }

        val newExpr = createExpressionFromText(buf.toString, expr)(expr.getManager)
        if (isValidExpr(newExpr, expr.parameters.length))
          Right(newExpr)
        else
          Left(ScalaInspectionBundle.message("introduce.implicit.not.allowed.here"))
      }
    case None =>
      Left(ScalaInspectionBundle.message("introduce.implicit.not.allowed.here"))
  }
}
