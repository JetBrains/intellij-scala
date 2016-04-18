package org.jetbrains.plugins.scala
package codeInspection.implicits

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInsight.intention.types.{AddOnlyStrategy, ToggleTypeAnnotation}
import org.jetbrains.plugins.scala.codeInspection.implicits.NoReturnTypeForImplicitDefInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.project.ProjectExt

/**
 * Nikolay.Tropin
 * 2014-09-23
 */
class NoReturnTypeForImplicitDefInspection extends AbstractInspection(id, description){
  override def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case fun: ScFunctionDefinition if fun.hasModifierProperty("implicit") &&
            fun.parameters.size == 1 &&
            !fun.paramClauses.clauses.exists(_.isImplicit) &&
            fun.returnTypeElement.isEmpty =>
      val descr = description
      val range = new TextRange(0, fun.parameterList.getTextRange.getEndOffset - fun.getModifierList.getTextRange.getStartOffset)
      holder.registerProblem(fun, range, descr, new AddReturnTypeQuickFix(fun))
  }
}

object NoReturnTypeForImplicitDefInspection {
  val id = "NoReturnTypeImplicitDef"
  val description = "No return type for implicit function"
}

class AddReturnTypeQuickFix(td: ScTypedDefinition) extends AbstractFixOnPsiElement("Add explicit return type", td) {
  override def doApplyFix(project: Project): Unit = {
    ToggleTypeAnnotation.complete(AddOnlyStrategy.withoutEditor, getElement)(project.typeSystem)
  }
}
