package org.jetbrains.plugins.scala.codeInspection.syntacticClarification

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.syntacticClarification.ExpandUpdateCallExplicitFix.Hint
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignStmt, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.transformation.RenamedReference

class ExpandUpdateCallInspection extends AbstractInspection {
  override def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case e@ScAssignStmt(ScMethodCall(r@RenamedReference(_, "update"), keys), Some(value)) =>
      holder.registerProblem(
        e,
        "Can replace to update method call",
        new ExpandUpdateCallExplicitFix(e)
      )
  }
}

class ExpandUpdateCallExplicitFix(stmt: ScAssignStmt)
    extends AbstractFixOnPsiElement(Hint, stmt) {

  override def doApplyFix(project: Project): Unit = {
    val statement = getElement
    statement match {
      case e @ ScAssignStmt(ScMethodCall(r @ RenamedReference(_, "update"), keys), Some(value)) =>
        val args = keys.map(_.getText) :+ value.getText
        val newExpression = s"${r.getText}.update(${args.mkString(", ")}))"
        val newCall = createExpressionFromText(newExpression)(e.getManager)
        e.replaceExpression(newCall, removeParenthesis = false)
      case _ =>
        //Do nothing
    }
  }
}

object ExpandUpdateCallExplicitFix {
  val Hint = "Explicit update fix"
}
