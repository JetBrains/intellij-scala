package org.jetbrains.plugins.scala
package codeInspection.syntacticClarification

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.syntacticClarification.AutoTuplingInspection.message
import org.jetbrains.plugins.scala.codeInspection.syntacticClarification.MakeTuplesExplicitFix.hint
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

/**
 * Nikolay.Tropin
 * 2014-09-26
 */
class AutoTuplingInspection extends AbstractInspection("Auto-tupling") {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case mc @ ScMethodCall(ref: ScReferenceExpression, _) if ref.bind().exists(_.tuplingUsed) =>
      holder.registerProblem(mc.args, message, new MakeTuplesExplicitFix(mc))
  }
}

object AutoTuplingInspection {
  val message = "Scala compiler will replace this argument list with tuple"
}

class MakeTuplesExplicitFix(invoc: MethodInvocation) extends AbstractFixOnPsiElement(hint, invoc) {

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

object MakeTuplesExplicitFix {
  val hint = "Make tuple explicit"
}