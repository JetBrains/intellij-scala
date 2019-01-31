package org.jetbrains.plugins.scala
package codeInsight
package intention
package booleans

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.mutable

/**
 * @author Ksenia.Sautina
 * @since 4/20/12
 */
final class FlipComparisonInMethodCallExprIntention extends PsiElementBaseIntentionAction {

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val methodCallExpr: ScMethodCall = PsiTreeUtil.getParentOfType(element, classOf[ScMethodCall], false)
    if (methodCallExpr == null) return false
    if (!methodCallExpr.getInvokedExpr.isInstanceOf[ScReferenceExpression]) return false

    val oper = methodCallExpr.getInvokedExpr.asInstanceOf[ScReferenceExpression].nameId.getText

    if (oper != "equals" && oper != "==" && oper != "!=" && oper != "eq" && oper != "ne" &&
            oper != ">" && oper != "<" && oper != ">=" && oper != "<=")
      return false

    val range: TextRange = methodCallExpr.getInvokedExpr.asInstanceOf[ScReferenceExpression].nameId.getTextRange
    val offset = editor.getCaretModel.getOffset
    if (!(range.getStartOffset <= offset && offset <= range.getEndOffset)) return false

    val notChanged = mutable.HashSet[String]("==", "!=", "equals", "eq", "ne")
    if (notChanged.contains(oper)) {
      setText("Flip '" + oper + "'" )
    }   else  {
      val replaceOper = Map(">" -> "<", "<" -> ">", ">=" -> "<=", "<=" -> ">=")
      setText("Flip '" + oper + "' to '" + replaceOper(oper) + "'")
    }

    if (methodCallExpr.getInvokedExpr.asInstanceOf[ScReferenceExpression].isQualified) return true

    false
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    implicit val ctx: ProjectContext = project

    val methodCallExpr: ScMethodCall = PsiTreeUtil.getParentOfType(element, classOf[ScMethodCall], false)
    if (methodCallExpr == null || !methodCallExpr.isValid) return

    val start = methodCallExpr.getTextRange.getStartOffset
    val diff = editor.getCaretModel.getOffset - methodCallExpr.getInvokedExpr.asInstanceOf[ScReferenceExpression].
      nameId.getTextRange.getStartOffset
    val expr = new StringBuilder
    val qualBuilder = new StringBuilder
    val argsBuilder = new StringBuilder
    val replaceOper = Map("equals" -> "equals","==" -> "==", "!=" -> "!=", "eq" -> "eq", "ne" -> "ne",
      ">" -> "<", "<" -> ">", ">=" -> "<=", "<=" -> ">=")

    argsBuilder.append(methodCallExpr.args.getText)

    analyzeMethodCallArgs(methodCallExpr.args, argsBuilder)

    val qual = methodCallExpr.getInvokedExpr.asInstanceOf[ScReferenceExpression].qualifier.get
    qualBuilder.append(qual.getText)
    var newArgs = qual.getText
    if (!(newArgs.startsWith("(") && newArgs.endsWith(")"))) {
      newArgs = qualBuilder.insert(0, "(").append(")").toString()
    }

    var newQual = argsBuilder.toString()
    if (newQual.startsWith("(") && newQual.endsWith(")")) {
      newQual = argsBuilder.toString().drop(1).dropRight(1)
    }

    val newQualExpr = createExpressionFromText(newQual)

    expr.append(methodCallExpr.args.getText).append(".").
            append(replaceOper(methodCallExpr.getInvokedExpr.asInstanceOf[ScReferenceExpression].nameId.getText)).
            append(newArgs)

    val newMethodCallExpr = createExpressionFromText(expr.toString())

    newMethodCallExpr.asInstanceOf[ScMethodCall].getInvokedExpr.asInstanceOf[ScReferenceExpression].qualifier.get.replaceExpression(newQualExpr, removeParenthesis = true)

    val size = newMethodCallExpr.asInstanceOf[ScMethodCall].getInvokedExpr.asInstanceOf[ScReferenceExpression].nameId.
            getTextRange.getStartOffset - newMethodCallExpr.getTextRange.getStartOffset

    inWriteAction {
      methodCallExpr.replaceExpression(newMethodCallExpr, removeParenthesis = true)
      editor.getCaretModel.moveToOffset(start + diff + size)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }

  override def getFamilyName: String = FlipComparisonInMethodCallExprIntention.FamilyName

  override def getText: String = getFamilyName
}

object FlipComparisonInMethodCallExprIntention {

  private[booleans] val FamilyName = "Flip comparison in method call expression."
}