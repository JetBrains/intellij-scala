package org.jetbrains.plugins.scala.lang
package psi
package light.scala

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.types.ScType

import scala.annotation.tailrec

/**
  * @author Alefas
  * @since 04/04/14.
  */
final class ScLightBindingPattern(override protected val delegate: ScBindingPattern)
                                 (implicit private val returnType: ScType)
  extends ScLightElement(delegate) with ScBindingPattern {

  override def getNavigationElement: PsiElement = super.getNavigationElement

  override def getOriginalElement: PsiElement = super.getOriginalElement

  override def isWildcard: Boolean = delegate.isWildcard

  override def getParent: PsiElement = delegate.getParent

  override def `type`() = Right(returnType)
}

object ScLightBindingPattern {

  @tailrec
  def apply(pattern: ScBindingPattern)
           (implicit returnType: ScType): ScLightBindingPattern = pattern match {
    case light: ScLightBindingPattern => apply(light.delegate)
    case _ => new ScLightBindingPattern(pattern)
  }
}