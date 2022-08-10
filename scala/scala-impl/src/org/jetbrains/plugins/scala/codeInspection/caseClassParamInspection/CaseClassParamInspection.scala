package org.jetbrains.plugins.scala
package codeInspection
package caseClassParamInspection

import com.intellij.codeInspection._
import com.intellij.codeInspection.ex.ProblemDescriptorImpl
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.util.EnumSet._

import scala.annotation.nowarn

@nowarn("msg=" + AbstractInspection.DeprecationText)
class CaseClassParamInspection extends AbstractInspection() {

  override protected def actionFor(implicit holder: ProblemsHolder, isOnTheFly: Boolean): PartialFunction[PsiElement, Any] = {
    case c: ScClass if c.isCase =>
      for{
        paramClause <- c.allClauses.take(1)
        classParam@(__ : ScClassParameter) <- paramClause.parameters
        if classParam.isVal && classParam.isCaseClassVal && !hasExplicitModifier(classParam)
      } {
        val descriptor = new ProblemDescriptorImpl(classParam, classParam,
          ScalaBundle.message("val.on.case.class.param.redundant"), Array(new RemoveValQuickFix(classParam)),
          ProblemHighlightType.LIKE_UNUSED_SYMBOL, false, TextRange.create(0, 3), holder.isOnTheFly)
        holder.registerProblem(descriptor)
      }
  }

  private def hasExplicitModifier(owner: ScModifierListOwner): Boolean = !owner.getModifierList.modifiers.isEmpty
}