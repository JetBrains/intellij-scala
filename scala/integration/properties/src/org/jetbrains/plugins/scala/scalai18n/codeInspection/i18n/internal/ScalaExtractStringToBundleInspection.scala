package org.jetbrains.plugins.scala
package scalai18n
package codeInspection
package i18n
package internal

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection.{BatchQuickFix, CommonProblemDescriptor, InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractRegisteredInspection}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.format.AnyTopmostStringParts
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotationsHolder, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.internal.BundleExtraction.{BundleExtractionInfo, executeBundleExtraction}
import org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.internal.ScalaExtractStringToBundleInspection._

import java.util

//noinspection ScalaExtractStringToBundle
class ScalaExtractStringToBundleInspection extends AbstractRegisteredInspection {

  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix] = None,
                                           descriptionTemplate: String = getDisplayName,
                                           highlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                                          (implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] = {
    element match {
      case element@AnyTopmostStringParts(parts) if !shouldBeIgnored(element, parts) =>
        val isNaturalLang = containsNaturalLangString(element, parts)

        if (isNaturalLang || (isOnTheFly && !ApplicationManager.getApplication.isUnitTestMode )) {
          val quickFixes = Array[LocalQuickFix](new ScalaMoveToBundleQuickFix(element)) ++ maybeQuickFix
          val highlight =
            if (isNaturalLang) highlightType
            else ProblemHighlightType.INFORMATION // make quickfix available for all strings
          Some(manager.createProblemDescriptor(element, descriptionTemplate, isOnTheFly, quickFixes, highlight))
        } else None
      case _ => None
    }
  }
}

//noinspection ScalaExtractStringToBundle
object ScalaExtractStringToBundleInspection {
  private def containsNaturalLangString(element: PsiElement, parts: Seq[format.StringPart]): Boolean =
    parts.exists {
      case format.Text(s) => isNaturalLangString(element, s)
      case _ => false
    }

  private def isNaturalLangString(element: PsiElement, string: String): Boolean =
    ScalaI18nUtil.isPassedToNls(element)
    //string.length > 3 &&
    //hasAtLeastOneLetters(string) &&
    //!hasCamelCase(string)

  private lazy val letterRegex = raw"""\w""".r
  private def hasAtLeastOneLetters(string: String): Boolean =
    letterRegex.findFirstIn(string).isDefined

  private lazy val camelCaseRegex = raw"""\p{Lower}\p{Upper}""".r
  private def hasCamelCase(string: String): Boolean =
    camelCaseRegex.findFirstIn(string).isDefined

  private def shouldBeIgnored(element: PsiElement, parts: Seq[format.StringPart]): Boolean =
    hasNonNLSAnnotation(element) ||
      isTestSource(element) ||
      isInBundleMessageCall(element)

  private def hasNonNLSAnnotation(element: PsiElement): Boolean =
    element
      .withParents
      .collect { case holder: ScAnnotationsHolder => holder }
      .exists(_.hasAnnotation(AnnotationUtil.NON_NLS))

  private def isTestSource(element: PsiElement): Boolean = {
    val isInTestSourceContent = ProjectFileIndex.getInstance(element.getProject).isInTestSourceContent _
    element
      .getContainingFile.nullSafe
      .map(_.getVirtualFile)
      .exists(isInTestSourceContent)
  }

  private def isInBundleMessageCall(element: PsiElement): Boolean =
    element.asOptionOf[ScLiteral].exists(ScalaI18nUtil.mustBePropertyKey(_))

  private class ScalaMoveToBundleQuickFix(_element: ScExpression)
    extends AbstractFixOnPsiElement("Extract to bundle", _element) with BatchQuickFix {
    override def startInWriteAction(): Boolean = false
    override protected def doApplyFix(element: ScExpression)(implicit project: Project): Unit = {
      val parts = element match {
        case AnyTopmostStringParts(parts) => parts.flatMap(ExtractPart.from)
        case _ => return
      }

      executeBundleExtraction(element, parts, project) {
        case BundleExtractionInfo(bundleClassName, bundleQualifiedClassName, key, arguments ) =>
          // add import
          val importsHolder: ScImportsHolder =
            Option(PsiTreeUtil.getParentOfType(element, classOf[ScPackaging]))
              .getOrElse(element.getContainingFile.asInstanceOf[ScImportsHolder])
          importsHolder.addImportForPath(bundleQualifiedClassName)

          // replace string with message call
          val argString =
            if (arguments.isEmpty) ""
            else arguments.mkString(", ", ", ", "")
          element.replace(ScalaPsiElementFactory.createExpressionFromText(
            s"""$bundleClassName.message("$key"$argString)"""
          ))
      }
    }

    // find a way to disable batch mode... until then just show this message
    override def applyFix(project: Project, descriptors: Array[CommonProblemDescriptor], psiElementsToIgnore: util.List[PsiElement], refreshViews: Runnable): Unit =
      Messages.showErrorDialog(project, "Don' run ExtractStringToBundle inspection in batch mode", "Do not run in batch mode")
  }
}