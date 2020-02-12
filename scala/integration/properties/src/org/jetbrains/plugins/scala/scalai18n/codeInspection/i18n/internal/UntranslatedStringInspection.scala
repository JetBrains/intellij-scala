package org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.internal

import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.AbstractRegisteredInspection
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.ScalaI18nUtil
import org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.internal.UntranslatedStringInspection._

import scala.util.matching.Regex

class UntranslatedStringInspection extends AbstractRegisteredInspection {

  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix] = None,
                                           descriptionTemplate: String = getDisplayName,
                                           highlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                                          (implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] = {
    Option(element) collect {
      case element@ScStringLiteral(string) if isNaturalLangString(string) && !shouldBeIgnore(element) =>
        manager.createProblemDescriptor(element, descriptionTemplate, isOnTheFly, Array.empty[LocalQuickFix], highlightType)
    }
  }
}

object UntranslatedStringInspection {
  private def isNaturalLangString(string: String): Boolean =
    //string.length > 3 &&
    !hasCamelCase(string)

  lazy val camelCaseRegex: Regex = raw"""\p{Lower}\p{Upper}""".r
  def hasCamelCase(string: String): Boolean =
    camelCaseRegex.findFirstIn(string).isDefined


  private def shouldBeIgnore(element: ScStringLiteral): Boolean =
    hasNonNLSAnnotation(element) ||
      isTestSource(element) ||
      isInBundleMessageCall(element)

  private def hasNonNLSAnnotation(element: PsiElement): Boolean =
    element
      .withParents
      .collect { case holder: ScAnnotationsHolder => holder }
      .exists(_.hasAnnotation("org.jetbrains.annotations.NonNls"))

  private def isTestSource(element: PsiElement): Boolean = {
    //element.getContainingFile.toOption.exists(file => TestSourcesFilter.isTestSources(file.getVirtualFile, element.getProject))
    element.getContainingFile.toOption.exists(file => ProjectFileIndex.getInstance(element.getProject).isInTestSourceContent(file.getVirtualFile))
  }

  private def isInBundleMessageCall(element: ScStringLiteral): Boolean =
    ScalaI18nUtil.mustBePropertyKey(element)
}