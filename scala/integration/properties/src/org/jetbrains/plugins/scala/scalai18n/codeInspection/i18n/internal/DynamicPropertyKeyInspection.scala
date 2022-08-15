package org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.internal

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import org.jetbrains.plugins.scala.codeInspection.PsiElementVisitorSimple
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.ScalaI18nUtil

class DynamicPropertyKeyInspection extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case s: ScStringLiteral if s.isSimpleLiteral =>
    case e: ScExpression =>
      val isPassedToProperty = ScalaI18nUtil.isPassedToAnnotated(e, AnnotationUtil.PROPERTY_KEY)
      if (isPassedToProperty)
        holder.registerProblem(e, getDisplayName)
    case _ =>
  }
}
