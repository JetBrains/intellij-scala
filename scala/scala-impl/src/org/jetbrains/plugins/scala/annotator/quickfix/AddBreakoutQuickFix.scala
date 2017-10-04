package org.jetbrains.plugins.scala.annotator.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.extensions.ResolvesTo
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScForStatement, ScInfixExpr, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}

/**
 * @author Nikolay.Tropin
 */
class AddBreakoutQuickFix(expr: ScExpression) extends IntentionAction {
  override def getText: String = "Add `collection.breakOut` argument"

  override def getFamilyName: String = "Add `collection.breakOut`"

  override def invoke(project: Project, editor: Editor, psiFile: PsiFile): Unit = {
    def createWithClauses(text: String) =
      ScalaPsiElementFactory.createExpressionWithContextFromText(text + "(collection.breakOut)", expr.getContext, expr)


    expr match {
      case mc: ScMethodCall =>
        mc.replaceExpression(createWithClauses(mc.getText), removeParenthesis = true)
      case inf: ScInfixExpr =>
        val equivCall = ScalaPsiElementFactory.createEquivMethodCall(inf)
        inf.replaceExpression(createWithClauses(equivCall.getText), removeParenthesis = true)
      case forStmt: ScForStatement =>
        val withClauses = createWithClauses(s"(${forStmt.getText})")
        forStmt.replaceExpression(withClauses, removeParenthesis = true)
      case _ =>
    }
  }

  override def startInWriteAction(): Boolean = true

  override def isAvailable(project: Project, editor: Editor, psiFile: PsiFile): Boolean =
    AddBreakoutQuickFix.isAvailable(expr)
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
      case forStmt: ScForStatement =>
        forStmt.getDesugarizedExpr.exists(isAvailable)
      case _ => false
    }
  }

  def isImplicitCanBuildFromParam(p: ScParameter): Boolean = {
    p.getType(TypingContext.empty) match {
      case Success(tpe, _) if tpe.canonicalText.startsWith("_root_.scala.collection.generic.CanBuildFrom") => true
      case _ => false
    }
  }
}
