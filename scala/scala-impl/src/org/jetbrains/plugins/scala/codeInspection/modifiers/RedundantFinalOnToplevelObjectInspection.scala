package org.jetbrains.plugins.scala.codeInspection.modifiers

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.codeInspection.PsiElementVisitorSimple
import org.jetbrains.plugins.scala.lang.lexer.{ScalaModifier, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

final class RedundantFinalOnToplevelObjectInspection extends LocalInspectionTool with DumbAware {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case obj@ScObject.withModifierList(ml) if obj.isTopLevel && ml.isFinal =>
      val finalModifier = ml.findFirstChildByType(ScalaTokenTypes.kFINAL).getOrElse(obj.targetToken)
      val quickFix = new SetModifierQuickfix(obj, ScalaModifier.Final, set = false)
      holder.registerProblem(finalModifier, getDisplayName, quickFix)
    case _ =>
  }
}
