package org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.internal

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.AbstractRegisteredInspection
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.ScalaI18nUtil

class DynamicPropertyKeyInspection extends AbstractRegisteredInspection {

  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix] = None,
                                           descriptionTemplate: String = getDisplayName,
                                           highlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                                          (implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] = {
    def isNonInterpolatedString = element.isInstanceOf[ScStringLiteral] && !element.isInstanceOf[ScInterpolatedStringLiteral]
    def isPassedToProperty = ScalaI18nUtil.isPassedToAnnotated(element, AnnotationUtil.PROPERTY_KEY)

    (!isNonInterpolatedString && isPassedToProperty)
      .option {
        manager.createProblemDescriptor(element, descriptionTemplate, isOnTheFly, maybeQuickFix.toArray, highlightType)
      }
  }
}
