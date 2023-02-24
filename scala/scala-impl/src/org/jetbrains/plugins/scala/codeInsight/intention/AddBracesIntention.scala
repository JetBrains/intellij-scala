package org.jetbrains.plugins.scala.codeInsight.intention

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.editor.AutoBraceAdvertiser
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

/**
 * Jason Zaugg
 */

class AddBracesIntention extends PsiElementBaseIntentionAction {
  override def getFamilyName: String = ScalaBundle.message("family.name.add.braces")

  override def getText: String = ScalaBundle.message("add.braces.around.single.line.expression")

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    check(project, editor, element).isDefined && IntentionAvailabilityChecker.checkIntention(this, element)

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    if (element == null || !element.isValid) return
    check(project, editor, element) match {
      case Some(x) => x()
      case None =>
    }
  }

  private def check(project: Project, editor: Editor, element: PsiElement): Option[() => Unit] = {
    val classes = Seq(classOf[ScPatternDefinition], classOf[ScIf], classOf[ScFunctionDefinition], classOf[ScTry],
      classOf[ScFinallyBlock], classOf[ScWhile], classOf[ScDo])

    def isAncestorOfElement(ancestor: PsiElement) = PsiTreeUtil.isContextAncestor(ancestor, element, false)

    val expr: Option[ScExpression] = element.parentOfType(classes).flatMap {
      case ScPatternDefinition.expr(e) if isAncestorOfElement(e) => Some(e)
      case ifStmt: ScIf =>
        ifStmt.thenExpression.filter(isAncestorOfElement).orElse(ifStmt.elseExpression.filter(isAncestorOfElement))
      case funDef: ScFunctionDefinition =>
        funDef.body.filter(isAncestorOfElement)
      case tryExpr: ScTry =>
        tryExpr.expression.filter(isAncestorOfElement)
      case finallyBlock: ScFinallyBlock =>
        finallyBlock.expression.filter(isAncestorOfElement)
      case whileStmt: ScWhile =>
        whileStmt.expression.filter(isAncestorOfElement)
      case doStmt: ScDo =>
        doStmt.body.filter(isAncestorOfElement)
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
    oneLinerExpr.map { expr =>
      () => {
        CodeEditUtil.replaceChild(
          expr.getParent.getNode,
          expr.getNode,
          createExpressionFromText("{\n%s}".format(expr.getText), expr)(expr.getManager).getNode
        )

        if (!IntentionPreviewUtils.isIntentionPreviewActive)
          AutoBraceAdvertiser.advertiseAutoBraces(project)
      }
    }
  }
}
