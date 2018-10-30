package org.jetbrains.plugins.scala.lang
package psi
package light.scala

import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.{PsiElement, PsiIdentifier}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

abstract class ScLightElement[D <: ScNamedElement](protected val delegate: D)
  extends LightElement(delegate.getManager, delegate.getLanguage) with ScNamedElement {

  override final def nameId: PsiElement = delegate.nameId

  override final def name: String = super.name

  override final def toString: String = delegate.toString

  override def getNavigationElement: PsiElement = delegate.getNavigationElement

  override def getNameIdentifier: PsiIdentifier = delegate.getNameIdentifier

  override final def navigate(requestFocus: Boolean): Unit = delegate.navigate(requestFocus)

  override final def canNavigate: Boolean = delegate.canNavigate

  override final def canNavigateToSource: Boolean = delegate.canNavigateToSource

  override protected final def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    throw new UnsupportedOperationException("Operation on light element")

  override protected final def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T =
    throw new UnsupportedOperationException("Operation on light element")
}

object ScLightElement {

  def unapply(lightElement: LightElement): Option[ScNamedElement] = lightElement match {
    case light: ScLightElement[_] => Some(light.delegate)
    case _ => None
  }
}