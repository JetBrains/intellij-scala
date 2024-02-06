package org.jetbrains.plugins.scala
package scalai18n
package codeInspection
package i18n
package internal

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.{PsiElement, PsiReference}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScConstructorPattern, ScPatternArgumentList}
import org.jetbrains.plugins.scala.lang.psi.api.expr.MethodInvocation
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.ScalaI18nUtil._
import org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.internal.ReferencePassedToNlsInspection._

import scala.collection.mutable

class ReferencePassedToNlsInspection extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case element@(_: PsiReference | _: MethodInvocation) if isPassedToNls(element) =>
      resolveToNotNlsAnnotated(element).foreach {
        case Annotatable(ref) if isInProjectSource(ref) =>
          holder.registerProblem(element, getDisplayName, new AnnotateWithNls(ref))

        case _ =>
          holder.registerProblem(element, getDisplayName)
      }
    case _ =>
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
    element match {
      case ResolvesTo(ref) if evaluatesNotToNls(ref, found) =>
        Some(ref)
      case invocation: MethodInvocation =>
        invocation.getEffectiveInvokedExpr match {
          case ResolvesTo(ref) if evaluatesNotToNls(ref, found) =>
            Some(ref)
          case _ => None
        }
      case _ =>
        None
    }

  private def evaluatesNotToNls(ref: PsiElement, found: mutable.Set[PsiElement]): Boolean =
    if (!found.add(ref)) false
    else ref match {
      case _ if isAnnotatedWithNlsOrNlsSafe(ref) => false
      case _: PsiReference | _: MethodInvocation => resolveToNotNlsAnnotated(ref, found).isDefined
      case (pattern: ScBindingPattern) & Parent(Parent(ScConstructorPattern(ResolvesTo(unapply: ScFunctionDefinition), ScPatternArgumentList(args@_*)))) if unapply.isSynthetic && args.contains(pattern) =>
        val caseClassParam = originalCaseClassParameter(unapply, args.indexOf(pattern))
        !caseClassParam.exists(isAnnotatedWithNlsOrNlsSafe)
      case pattern: ScBindingPattern => evaluatesNotToNls(pattern.nameContext, found)
      case pd: ScPatternDefinition if pd.isEffectivelyFinal => pd.expr.exists(_.calculateTailReturns.exists(evaluatesNotToNls(_, found)))
      case func: ScFunctionDefinition if func.isEffectivelyFinal => func.returnUsages.exists(evaluatesNotToNls(_, found))
      case _ => true
    }
}
