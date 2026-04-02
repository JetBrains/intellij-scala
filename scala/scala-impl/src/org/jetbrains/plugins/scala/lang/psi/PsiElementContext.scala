package org.jetbrains.plugins.scala.lang.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.types.{ConformanceContext, TypePresentationContext}

/**
 * A unified context derived from a [[PsiElement]], combining [[ElementScope]], [[ConformanceContext]], and [[TypePresentationContext]].
 *
 * Use this instead of creating multiple separate context objects from the same element.
 *
 * @see [[https://youtrack.jetbrains.com/issue/SCL-23892/Unify-context-parameters]]
 */
trait PsiElementContext extends ElementScope with ConformanceContext with TypePresentationContext.PsiBased {
  def psiElement: PsiElement
}

object PsiElementContext {
  def apply(element: PsiElement): PsiElementContext = new PsiElementContextImpl(element)

  private class PsiElementContextImpl(override val psiElement: PsiElement)
    extends PsiElementContext
      with ConformanceContext.PsiBasedImpl
  {
    override val project: Project = psiElement.getProject
    override lazy val scope: GlobalSearchScope = psiElement.resolveScope
    override protected def placeForTypePresentation: PsiElement = psiElement

    override def toString: String = s"PsiElementContext($psiElement)"
  }
}
