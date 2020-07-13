package org.jetbrains.plugins.scala
package codeInsight.intention.expression

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.codeInsight.intention.expression.ConvertParameterToUnderscoreIntention._
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec
import scala.collection.mutable


class ConvertParameterToUnderscoreIntention extends PsiElementBaseIntentionAction {
  override def getFamilyName: String = ScalaBundle.message("family.name.convert.parameter.to.underscore.section")

  override def getText: String = getFamilyName

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val expr: ScFunctionExpr = PsiTreeUtil.getParentOfType(element, classOf[ScFunctionExpr], false)
    if (expr == null) return false

    val range: TextRange = expr.params.getTextRange
    val offset = editor.getCaretModel.getOffset
    if (range.getStartOffset <= offset && offset <= range.getEndOffset + 3) return true

    false
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    def showErrorHint(hint: String): Unit = {
      //noinspection ReferencePassedToNls
      if (ApplicationManager.getApplication.isUnitTestMode) throw new RuntimeException(hint)
      else HintManager.getInstance().showErrorHint(editor, hint)
    }

    val expr: ScFunctionExpr = PsiTreeUtil.getParentOfType(element, classOf[ScFunctionExpr], false)
    if (expr == null || !expr.isValid) return

    val startOffset = expr.getTextRange.getStartOffset

    createExpressionToIntroduce(expr, withoutParameterTypes = false) match {
      case Left(newExpr) =>
        inWriteAction {
          expr.replace(newExpr)
          editor.getCaretModel.moveToOffset(startOffset)
          PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
        }
      case Right(message) =>
        showErrorHint(message)
    }
  }
}

object ConvertParameterToUnderscoreIntention {
  def createExpressionToIntroduce(expr: ScFunctionExpr, withoutParameterTypes: Boolean): Either[ScExpression, String] = {
    implicit val ctx: ProjectContext = expr

    def seekParams(fun: ScFunctionExpr): mutable.HashMap[String, Int] = {
      val map: mutable.HashMap[String, Int] = new mutable.HashMap[String, Int]()
      var clearMap = false
      val visitor = new ScalaRecursiveElementVisitor {
        override def visitReferenceExpression(expr: ScReferenceExpression): Unit = {
          expr.resolve() match {
            case p: ScParameter if fun.parameters.contains(p) =>
              if (!map.keySet.contains(expr.getText)) {
                map.put(expr.getText, expr.getTextRange.getStartOffset)
              } else {
                clearMap = true
              }
            case _ =>
          }
          super.visitReferenceExpression(expr)
        }
      }
      fun.accept(visitor)
      if (clearMap) map.clear()
      map
    }

    @tailrec
    def isValidExpr(expr: ScExpression, paramCount: Int): Boolean = {
      if (ScUnderScoreSectionUtil.underscores(expr).length == paramCount) return true
      expr match {
        case ScBlockExpr.Expressions(head) => isValidExpr(head, paramCount)
        case ScParenthesisedExpr(e) => isValidExpr(e, paramCount)
        case _ => false
      }
    }

    val result = expr.result.getOrElse(return Right(ScalaInspectionBundle.message("introduce.implicit.not.allowed.here")))

    val buf = new StringBuilder
    buf.append(result.getText)

    val diff = result.getTextRange.getStartOffset
    var previousOffset = -1
    var occurrences: mutable.HashMap[String, Int] = new mutable.HashMap[String, Int]
    occurrences = seekParams(expr)

    if (occurrences.isEmpty || occurrences.size != expr.parameters.size)
      return Right(ScalaInspectionBundle.message("introduce.implicit.incorrect.count"))

    for (p <- expr.parameters) {
      if (!occurrences.keySet.contains(p.name) || occurrences(p.name) < previousOffset)
        return Right(ScalaInspectionBundle.message("introduce.implicit.incorrect.order"))
      previousOffset = occurrences(p.name)
    }

    for (p <- expr.parameters.reverse) {
      val expectedType = p.expectedParamType
      val declaredType = p.typeElement
      val newParam = declaredType match {
        case None => "_"
        case _ if withoutParameterTypes => "_"
        case Some(t) if expectedType.exists(_.equiv(t.`type`().getOrAny)) => "_"
        case Some(_) => s"(_: ${p.typeElement.get.getText})"
      }

      val offset = occurrences(p.name) - diff
      buf.replace(offset, offset + p.name.length, newParam)
    }

    val newExpr = createExpressionFromText(buf.toString())(expr.getManager)

    if (!isValidExpr(newExpr, expr.parameters.length))
      return Right(ScalaInspectionBundle.message("introduce.implicit.not.allowed.here"))

    Left(newExpr)
  }

}
