package org.jetbrains.plugins.scala.codeInspection.syntacticClarification

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

class AutoTuplingInspection extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case mc @ ScMethodCall(ref: ScReferenceExpression, _) if ref.bind().exists(_.tuplingUsed) =>
      holder.registerProblem(mc.args, ScalaInspectionBundle.message("scala.compiler.will.replace.this.argument.list.with.tuple"), new MakeTuplesExplicitFix(mc))
    case _ =>
  }
}

class MakeTuplesExplicitFix(invoc: MethodInvocation) extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("make.tuple.explicit"), invoc) {

  override protected def doApplyFix(element: MethodInvocation)
                                   (implicit project: Project): Unit = element match {
    case mc: ScMethodCall =>
      val newArgsText = s"(${mc.args.getText})"
      val invokedExprText = mc.getInvokedExpr.getText
      val newCall = createExpressionFromText(s"$invokedExprText$newArgsText", mc)
      mc.replaceExpression(newCall, removeParenthesis = false)
    case _ =>

  }
}
