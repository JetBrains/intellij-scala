package org.jetbrains.plugins.scala
package codeInsight.intention

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

/**
 * Jason Zaugg
 */

class AddBracesIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = "Add braces"

  override def getText = "Add braces around single line expression"

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    check(project, editor, element).isDefined && IntentionAvailabilityChecker.checkIntention(this, element)

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    if (element == null || !element.isValid) return
    check(project, editor, element) match {
      case Some(x) => x()
      case None =>
    }
  }

  private def check(project: Project, editor: Editor, element: PsiElement): Option[() => Unit] = {
    val classes = Seq(classOf[ScPatternDefinition], classOf[ScIfStmt], classOf[ScFunctionDefinition], classOf[ScTryBlock],
      classOf[ScFinallyBlock], classOf[ScWhileStmt], classOf[ScDoStmt])
    def isAncestorOfElement(ancestor: PsiElement) = PsiTreeUtil.isContextAncestor(ancestor, element, false)

    val expr: Option[ScExpression] = element.parentOfType(classes).flatMap {
      case ScPatternDefinition.expr(e) if isAncestorOfElement(e) => Some(e)
      case ifStmt: ScIfStmt =>
        ifStmt.thenBranch.filter(isAncestorOfElement).orElse(ifStmt.elseBranch.filter(isAncestorOfElement))
      case funDef: ScFunctionDefinition =>
        funDef.body.filter(isAncestorOfElement)
      case tryBlock: ScTryBlock if !tryBlock.hasRBrace =>
        tryBlock.statements match {
          case Seq(x: ScExpression) if isAncestorOfElement(x) => Some(x)
          case _ => None
        }
      case finallyBlock: ScFinallyBlock =>
        finallyBlock.expression.filter(isAncestorOfElement)
      case whileStmt: ScWhileStmt =>
        whileStmt.body.filter(isAncestorOfElement)
      case doStmt: ScDoStmt =>
        doStmt.getExprBody.filter(isAncestorOfElement)
      case _ => None
    }
    val oneLinerExpr: Option[ScExpression] = expr.filter {
      x =>
        val startLine = editor.getDocument.getLineNumber(x.getTextRange.getStartOffset)
        val endLine = editor.getDocument.getLineNumber(x.getTextRange.getEndOffset)
        val isBlock = x match {
          case _: ScBlockExpr => true
          case _ => false

        }
        startLine == endLine && !isBlock
    }
    oneLinerExpr.map {
      expr => () => {
        CodeEditUtil.replaceChild(expr.getParent.getNode, expr.getNode,
          createExpressionFromText("{\n%s}".format(expr.getText))(expr.getManager).getNode)
      }
    }
  }
}
