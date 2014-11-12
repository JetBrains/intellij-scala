package org.jetbrains.plugins.scala
package codeInspection.implicits

import com.intellij.codeInspection.{ProblemDescriptor, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInsight.intention.types.{ToggleTypeAnnotation, Update}
import org.jetbrains.plugins.scala.codeInspection.implicits.NoReturnTypeForImplicitDefInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFix, AbstractInspection}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition

/**
 * Nikolay.Tropin
 * 2014-09-23
 */
class NoReturnTypeForImplicitDefInspection extends AbstractInspection(id, description){
  override def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case fun: ScFunctionDefinition if fun.hasModifierProperty("implicit") && fun.returnTypeElement.isEmpty =>
      val descr = description
      val range = new TextRange(0, fun.parameterList.getTextRange.getEndOffset - fun.getModifierList.getTextRange.getStartOffset)
      holder.registerProblem(fun, range, descr, new AddReturnTypeQuickFix(fun))
  }
}

object NoReturnTypeForImplicitDefInspection {
  val id = "NoReturnTypeImplicitDef"
  val description = "No return type for implicit function"
}

class AddReturnTypeQuickFix(td: ScTypedDefinition) extends AbstractFix("Add explicit return type", td) {
  override def doApplyFix(project: Project): Unit = {
    (new ToggleTypeAnnotation).complete(Update, td.getFirstChild)
  }
}
