package org.jetbrains.plugins.scala
package scalai18n
package codeInspection
package i18n
package internal

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractRegisteredInspection}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression}
import org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.internal.ReferencePassedToNlsInspection._

class ReferencePassedToNlsInspection extends AbstractRegisteredInspection {

  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix] = None,
                                           descriptionTemplate: String = getDisplayName,
                                           highlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                                          (implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] = {
    val passedToNls = ScalaI18nUtil.isPassedToAnnotated(_: PsiElement, AnnotationUtil.NLS)
    val expr = element match {
      case (expr: ScExpression) && ResolvesTo(ref) if passedToNls(expr) =>
        Some(expr -> ref)
      case invocation: MethodInvocation if passedToNls(invocation) =>
        invocation.getEffectiveInvokedExpr match {
          case ResolvesTo(ref) =>
            Some(invocation -> ref)
          case _ => None
        }
      case _ => None
    }

    expr
      .filterNot { case (_, ref) => ScalaI18nUtil.isAnnotatedWith(ref, AnnotationUtil.NLS) }
      .map {
        case (expr, Annotatable(ref)) if isInProjectSource(ref) =>
          val quickFixes = Array[LocalQuickFix](new AnnotateWithNls(ref)) ++ maybeQuickFix
          manager.createProblemDescriptor(expr, descriptionTemplate, isOnTheFly, quickFixes, highlightType)

        case (expr, _) =>
          val quickFixes = Array[LocalQuickFix]() ++ maybeQuickFix
          manager.createProblemDescriptor(expr, descriptionTemplate, isOnTheFly, quickFixes, highlightType)
      }
  }
}

object ReferencePassedToNlsInspection {
  def isInProjectSource(e: PsiElement): Boolean = {
    val projectFileIndex = ProjectFileIndex.getInstance(e.getProject)
    e.getContainingFile.nullSafe.map(_.getVirtualFile).exists(projectFileIndex.isInSourceContent)
  }


  private class AnnotateWithNls(_element: ScAnnotationsHolder)
    extends AbstractFixOnPsiElement("Annotate with @Nls", _element) {

    override protected def doApplyFix(element: ScAnnotationsHolder)(implicit project: Project): Unit = {
      element.addAnnotation(AnnotationUtil.NLS)
    }
  }

  object Annotatable {
    def unapply(psiElement: PsiElement): Option[ScAnnotationsHolder] =
      psiElement match {
        case holder: ScAnnotationsHolder => Some(holder)
        case pattern: ScBindingPattern => pattern.nameContext.asOptionOf[ScAnnotationsHolder]
        case _ => None
      }
  }
}
