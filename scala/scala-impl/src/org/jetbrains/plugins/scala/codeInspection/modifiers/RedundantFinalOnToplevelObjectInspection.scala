package org.jetbrains.plugins.scala
package codeInspection
package modifiers

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import org.jetbrains.plugins.scala.lang.lexer.{ScalaModifier, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

class RedundantFinalOnToplevelObjectInspection extends LocalInspectionTool {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
      case obj@ScObject.withModifierList(ml) if obj.isTopLevel && ml.isFinal =>
        val finalModifier = ml.findFirstChildByType(ScalaTokenTypes.kFINAL).getOrElse(obj.targetToken)
        val quickFix = new SetModifierQuickfix(obj, ScalaModifier.Final, set = false)
        holder.registerProblem(finalModifier, getDisplayName, quickFix)
      case _ =>
    }
}

