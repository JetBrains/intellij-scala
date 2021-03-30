package org.jetbrains.plugins.scala
package codeInsight
package intention
package expression

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

class ConvertToInfixExpressionIntention extends PsiElementBaseIntentionAction {
  override def getFamilyName: String = ScalaBundle.message("family.name.convert.to.infix.expression")

  override def getText: String = getFamilyName

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    if (!IntentionAvailabilityChecker.checkIntention(this, element)) return false
    val methodCallExpr : ScMethodCall = PsiTreeUtil.getParentOfType(element, classOf[ScMethodCall], false)
    if (methodCallExpr == null) return false
    val referenceExpr = methodCallExpr.getInvokedExpr match {
      case ref: ScReferenceExpression => ref
      case call: ScGenericCall => Option(call.referencedExpr) match { //if the expression has type args
        case Some(ref: ScReferenceExpression) => ref
        case _ => return false
      }
      case _ => return false
    }
    val range: TextRange = referenceExpr.nameId.getTextRange
    val offset = editor.getCaretModel.getOffset
    if (!(range.getStartOffset <= offset && offset <= range.getEndOffset)) return false
    if (referenceExpr.isQualified) return true
    false
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    implicit val ctx: ProjectContext = project

    val methodCallExpr: ScMethodCall = PsiTreeUtil.getParentOfType(element, classOf[ScMethodCall], false)
    if (methodCallExpr == null || !methodCallExpr.isValid) return

    val referenceExpr = methodCallExpr.getInvokedExpr match {
      case ref: ScReferenceExpression => ref
      case call: ScGenericCall => Option(call.referencedExpr) match { //if the expression has type args
        case Some(ref: ScReferenceExpression) => ref
        case _ => return
      }
      case _ => return
    }
    val start = methodCallExpr.getTextRange.getStartOffset
    val diff = editor.getCaretModel.getOffset - referenceExpr.nameId.getTextRange.getStartOffset

    var putArgsFirst = false
    val argsBuilder = new StringBuilder
    val invokedExprBuilder = new StringBuilder

    val qual = referenceExpr.qualifier.get
    val operText = methodCallExpr.getInvokedExpr match {
      case call: ScGenericCall => referenceExpr.nameId.getText ++ call.typeArgs.getText
      case _ =>  referenceExpr.nameId.getText
    }
    val invokedExprText = methodCallExpr.getInvokedExpr.getText
    val methodCallArgs = methodCallExpr.args

    if (invokedExprText.last == ':') {
      putArgsFirst = true
      invokedExprBuilder.append(operText).append(" ").append(qual.getText)
    } else {
      invokedExprBuilder.append(qual.getText).append(" ").append(operText)
    }

    argsBuilder.append(methodCallArgs.getText)

    analyzeMethodCallArgs(methodCallArgs, argsBuilder)

    var forA = qual.getText
    if (forA.startsWith("(") && forA.endsWith(")")) {
      forA = qual.getText.drop(1).dropRight(1)
    }

    var forB = argsBuilder.toString()
    if (forB.startsWith("(") && forB.endsWith(")")) {
      forB = argsBuilder.toString().drop(1).dropRight(1)
    }

    val expr = if (putArgsFirst) {
      argsBuilder.append(" ").append(invokedExprBuilder)
    } else {
      invokedExprBuilder.append(" ").append(argsBuilder)
    }
    val text = expr.toString()

    createExpressionFromText(text) match {
      case infix@ScInfixExpr.withAssoc(base, operation, argument) =>
        base.replaceExpression(createExpressionFromText(forA), removeParenthesis = true)
        argument.replaceExpression(createExpressionFromText(forB), removeParenthesis = true)

        val size = operation.nameId.getTextRange.getStartOffset - infix.getTextRange.getStartOffset

        inWriteAction {
          methodCallExpr.replaceExpression(infix, removeParenthesis = true)
          editor.getCaretModel.moveToOffset(start + diff + size)
          PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
        }
      case _ => throw new IllegalStateException(s"$text should be infix expression")
    }
  }

}
