package org.jetbrains.plugins.scala.annotator.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.extensions.ResolvesTo
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScFor, ScInfixExpr, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

class AddBreakoutQuickFix(expr: ScExpression) extends IntentionAction {
  override def getText: String = ScalaBundle.message("add.collection.breakout.argument")

  override def getFamilyName: String = ScalaBundle.message("family.name.add.collection.breakout")

  override def invoke(project: Project, editor: Editor, psiFile: PsiFile): Unit = addBreakoutArgument(expr)

  override def startInWriteAction(): Boolean = true

  override def isAvailable(project: Project, editor: Editor, psiFile: PsiFile): Boolean =
    AddBreakoutQuickFix.isAvailable(expr)

  override def generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
    if (addBreakoutArgument(PsiTreeUtil.findSameElementInCopy(expr, file))) IntentionPreviewInfo.DIFF
    else IntentionPreviewInfo.EMPTY

  private def addBreakoutArgument(expression: ScExpression): Boolean = {
    def createWithClauses(text: String) =
      ScalaPsiElementFactory.createExpressionWithContextFromText(text + "(collection.breakOut)", expression.getContext, expression)

    expression match {
      case mc: ScMethodCall =>
        mc.replaceExpression(createWithClauses(mc.getText), removeParenthesis = true)
        true
      case inf: ScInfixExpr =>
        val equivCall = ScalaPsiElementFactory.createEquivMethodCall(inf)
        inf.replaceExpression(createWithClauses(equivCall.getText), removeParenthesis = true)
        true
      case forStmt: ScFor =>
        val withClauses = createWithClauses(s"(${forStmt.getText})")
        forStmt.replaceExpression(withClauses, removeParenthesis = true)
        true
      case _ => false
    }
  }
}

object AddBreakoutQuickFix {
  def isAvailable(expr: ScExpression): Boolean = {
    if (!expr.isValid) return false
    expr match {
      case MethodRepr(_, _, Some(ResolvesTo(fd: ScFunctionDefinition)), _) =>
        val lastClause = fd.paramClauses.clauses.lastOption
        lastClause.map(_.parameters) match {
          case Some(Seq(p: ScParameter)) if isImplicitCanBuildFromParam(p) => true
          case _ => false
        }
      case forStmt: ScFor =>
        forStmt.desugared().exists(isAvailable)
      case _ => false
    }
  }

  def isImplicitCanBuildFromParam(p: ScParameter): Boolean = {
    p.`type`() match {
      case Right(tpe) if tpe.canonicalText.startsWith("_root_.scala.collection.generic.CanBuildFrom") => true
      case _ => false
    }
  }
}
