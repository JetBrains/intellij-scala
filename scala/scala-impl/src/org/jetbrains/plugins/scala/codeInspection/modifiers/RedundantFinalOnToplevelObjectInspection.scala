package org.jetbrains.plugins.scala.codeInspection.modifiers

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.codeInspection.PsiElementVisitorSimple
import org.jetbrains.plugins.scala.lang.lexer.{ScalaModifier, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.{ScFile, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel

final class RedundantFinalOnToplevelObjectInspection extends LocalInspectionTool with DumbAware {
  override def isAvailableForFile(file: PsiFile): Boolean = file match {
    case file: ScalaFile =>
      // Since Scala 2.13 'final' is redundant on all objects.
      // We add that warning in [[org.jetbrains.plugins.scala.annotator.modifiers.ModifierChecker]]
      file.scalaLanguageLevel.forall(_ < ScalaLanguageLevel.Scala_2_13)
    case _ =>
      false
  }

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case obj@ScObject.withModifierList(ml) if obj.isTopLevel && ml.isFinal =>
      val finalModifier = ml.findFirstChildByType(ScalaTokenTypes.kFINAL).getOrElse(obj.targetToken)
      val quickFix = new SetModifierQuickfix(obj, ScalaModifier.Final, set = false)
      holder.registerProblem(finalModifier, getDisplayName, quickFix)
    case _ =>
  }
}
