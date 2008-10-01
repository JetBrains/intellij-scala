package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTyped}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.types._

trait ScBindingPattern extends ScPattern with ScNamedElement with ScTyped {

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  def isWildcard: Boolean

  override def calcType() : ScType
}