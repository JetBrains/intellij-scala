package org.jetbrains.plugins.scala
package codeInspection.parameters

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.parameters.AutoTuplingInspection.message
import org.jetbrains.plugins.scala.codeInspection.parameters.MakeTuplesExplicitFix.hint
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * Nikolay.Tropin
 * 2014-09-26
 */
class AutoTuplingInspection extends AbstractInspection("Auto-tupling") {
  override def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case mc @ ScMethodCall(ref: ScReferenceExpression, _) if ref.bind().exists(_.tuplingUsed) =>
      holder.registerProblem(mc.args, message, new MakeTuplesExplicitFix(mc))
  }
}

object AutoTuplingInspection {
  val message = "Scala compiler will replace this argument list with tuple"
}

class MakeTuplesExplicitFix(invoc: MethodInvocation) extends AbstractFixOnPsiElement(hint, invoc) {
  override def doApplyFix(project: Project): Unit = {
    val mInvoc = getElement
    mInvoc match {
      case mc: ScMethodCall =>
        val newArgsText = s"(${mc.args.getText})"
        val invokedExprText = mc.getInvokedExpr.getText
        val newCall = ScalaPsiElementFactory.createExpressionFromText(s"$invokedExprText$newArgsText", mc.getManager)
        mc.replaceExpression(newCall, removeParenthesis = false)
      case _ =>
    }
  }
}

object MakeTuplesExplicitFix {
  val hint = "Make tuple explicit"
}