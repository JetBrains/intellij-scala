package org.jetbrains.plugins.scala
package lang.psi.light.scala

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.light.LightElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.light.LightUtil
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
 * @author Alefas
 * @since 04/04/14.
 */
class ScLightBindingPattern(rt: ScType, val b: ScBindingPattern)
  extends LightElement(b.getManager, b.getLanguage) with ScBindingPattern {

  override def nameId: PsiElement = b.nameId

  override def isWildcard: Boolean = b.isWildcard

  override def getParent: PsiElement = b.getParent

  override def `type`(): TypeResult = Right(rt)

  override def getOriginalElement: PsiElement = super[ScBindingPattern].getOriginalElement

  override def toString: String = b.toString

  override def getNavigationElement: PsiElement = LightUtil.originalNavigationElement(b)

  override def navigate(requestFocus: Boolean): Unit = b.navigate(requestFocus)

  override def canNavigate: Boolean = b.canNavigate

  override def canNavigateToSource: Boolean = b.canNavigateToSource

  override protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    throw new UnsupportedOperationException("Operation on light element")

  override protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T =
    throw new UnsupportedOperationException("Operation on light element")
}
