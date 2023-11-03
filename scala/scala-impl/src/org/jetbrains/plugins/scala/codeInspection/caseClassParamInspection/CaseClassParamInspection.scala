package org.jetbrains.plugins.scala.codeInspection.caseClassParamInspection

import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInspection.PsiElementVisitorSimple
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.util.EnumSet._

class CaseClassParamInspection extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case c: ScClass if c.isCase =>
      for {
        paramClause <- c.allClauses.take(1)
        classParam@(__ : ScClassParameter) <- paramClause.parameters
        if classParam.isVal && classParam.isCaseClassVal && !hasExplicitModifier(classParam)
      } {
        val valToken = classParam.findFirstChildByType(ScalaTokenTypes.kVAL)
        val errorElement = valToken.getOrElse(classParam)

        holder.registerProblem(
          errorElement,
          ScalaBundle.message("val.on.case.class.param.redundant"),
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
          new RemoveValQuickFix(classParam)
        )
      }
    case _ =>
  }

  private def hasExplicitModifier(owner: ScModifierListOwner): Boolean = !owner.getModifierList.modifiers.isEmpty
}