package org.jetbrains.plugins.scala
package scalai18n
package codeInspection
package i18n
package internal

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.{PsiElement, PsiReference}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractRegisteredInspection}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.MethodInvocation
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.ScalaI18nUtil._
import org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.internal.ReferencePassedToNlsInspection._

import scala.collection.mutable

class ReferencePassedToNlsInspection extends AbstractRegisteredInspection {

  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix] = None,
                                           descriptionTemplate: String = getDisplayName,
                                           highlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                                          (implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] = {
    val expr = element match {
      case _: PsiReference | _: MethodInvocation if isPassedToAnnotated(element, AnnotationUtil.NLS) =>
        resolveToNotNlsAnnotated(element)
      case _ =>
        None
    }

    expr
      .map {
        case Annotatable(ref) if isInProjectSource(ref) =>
          val quickFixes = Array[LocalQuickFix](new AnnotateWithNls(ref)) ++ maybeQuickFix
          manager.createProblemDescriptor(element, descriptionTemplate, isOnTheFly, quickFixes, highlightType)

        case _ =>
          val quickFixes = Array[LocalQuickFix]() ++ maybeQuickFix
          manager.createProblemDescriptor(element, descriptionTemplate, isOnTheFly, quickFixes, highlightType)
      }
  }
}

object ReferencePassedToNlsInspection {
  def isInProjectSource(e: PsiElement): Boolean = {
    val projectFileIndex = ProjectFileIndex.getInstance(e.getProject)
    e.getContainingFile.nullSafe.map(_.getVirtualFile).exists(projectFileIndex.isInSourceContent)
  }


  //noinspection ScalaExtractStringToBundle
  private class AnnotateWithNls(_element: ScAnnotationsHolder)
    extends AbstractFixOnPsiElement("Annotate with @Nls", _element) {

    override protected def doApplyFix(element: ScAnnotationsHolder)(implicit project: Project): Unit = {
      element.addAnnotation(AnnotationUtil.NLS)
    }
  }

  private object Annotatable {
    def unapply(psiElement: PsiElement): Option[ScAnnotationsHolder] =
      psiElement match {
        case holder: ScAnnotationsHolder => Some(holder)
        case pattern: ScBindingPattern => pattern.nameContext.asOptionOf[ScAnnotationsHolder]
        case _ => None
      }
  }

  private def resolveToNotNlsAnnotated(element: PsiElement, found: mutable.Set[PsiElement] = mutable.Set.empty): Option[PsiElement] =
    if (!found.add(element)) None
    else element match {
      case ResolvesTo(ref) if evaluatesNotToNls(ref, found) => Some(ref)
      case invocation: MethodInvocation =>
        invocation.getEffectiveInvokedExpr match {
          case ResolvesTo(ref) if evaluatesNotToNls(ref, found) =>
            Some(ref)
          case _ => None
        }
      case _ => None
    }

  private def evaluatesNotToNls(ref: PsiElement, found: mutable.Set[PsiElement]): Boolean = {
    if (!found.add(ref)) false
    else ref match {
      case _ if isAnnotatedWith(ref, AnnotationUtil.NLS) => false
      case _: PsiReference | _: MethodInvocation => resolveToNotNlsAnnotated(ref, found).isDefined
      case pattern: ScBindingPattern => evaluatesNotToNls(pattern.nameContext, found)
      case pd: ScPatternDefinition => pd.expr.exists(_.calculateTailReturns.exists(evaluatesNotToNls(_, found)))
      case func: ScFunctionDefinition => func.returnUsages.exists(evaluatesNotToNls(_, found))
      case _ => true
    }
  }
}
