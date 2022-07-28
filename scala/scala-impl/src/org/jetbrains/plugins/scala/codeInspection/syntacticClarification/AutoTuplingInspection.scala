package org.jetbrains.plugins.scala
package codeInspection.syntacticClarification

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

import scala.annotation.nowarn

@nowarn("msg=" + AbstractInspection.DeprecationText)
class AutoTuplingInspection extends AbstractInspection(ScalaInspectionBundle.message("display.name.auto.tupling")) {

  override def actionFor(implicit holder: ProblemsHolder, isOnTheFly: Boolean): PartialFunction[PsiElement, Any] = {
    case mc @ ScMethodCall(ref: ScReferenceExpression, _) if ref.bind().exists(_.tuplingUsed) =>
      holder.registerProblem(mc.args, ScalaInspectionBundle.message("scala.compiler.will.replace.this.argument.list.with.tuple"), new MakeTuplesExplicitFix(mc))
  }
}

class MakeTuplesExplicitFix(invoc: MethodInvocation) extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("make.tuple.explicit"), invoc) {

  override protected def doApplyFix(element: MethodInvocation)
                                   (implicit project: Project): Unit = element match {
    case mc: ScMethodCall =>
      val newArgsText = s"(${mc.args.getText})"
      val invokedExprText = mc.getInvokedExpr.getText
      val newCall = createExpressionFromText(s"$invokedExprText$newArgsText")
      mc.replaceExpression(newCall, removeParenthesis = false)
    case _ =>

  }
}
