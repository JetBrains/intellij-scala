package org.jetbrains.plugins.scala.codeInspection.modifiers

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import org.jetbrains.plugins.scala.codeInspection.PsiElementVisitorSimple
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

class MarkInnerCaseObjectsAsFinal extends LocalInspectionTool {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
      case obj@ScObject.withModifierList(ml) if !obj.isTopLevel && !ml.isFinal && ml.isCase =>
        val quickFix = new SetModifierQuickfix(obj, ScalaModifier.Final, set = true)
        holder.registerProblem(obj.targetToken, getDisplayName, quickFix)
      case _ =>
    }
}
