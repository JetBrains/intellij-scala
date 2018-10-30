package org.jetbrains.plugins.scala.lang
package psi
package light.scala

import com.intellij.psi.{PsiAnnotation, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}

abstract class ScLightModifierOwner[M <: ScNamedElement with ScModifierListOwner](override protected val delegate: M)
  extends ScLightElement(delegate) with ScModifierListOwner {

  override def getNavigationElement: PsiElement = super[ScLightElement].getNavigationElement

  override def getModifierList: ScModifierList = delegate.getModifierList

  override final def psiAnnotations: Array[PsiAnnotation] = delegate.getAnnotations

  override final def getApplicableAnnotations: Array[PsiAnnotation] = delegate.getApplicableAnnotations

  override final def findAnnotation(qualifiedName: String): PsiAnnotation = delegate.findAnnotation(qualifiedName)

  override final def addAnnotation(qualifiedName: String): PsiAnnotation = delegate.addAnnotation(qualifiedName)

  override final def hasAnnotation(qualifiedName: String): Boolean = delegate.hasAnnotation(qualifiedName)

  override final def annotations: Seq[ScAnnotation] = delegate.annotations
}
