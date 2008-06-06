package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTyped}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.types._

trait ScBindingPattern extends ScPattern with ScNamedElement with ScTyped {

  def isWildcard: Boolean

  import org.jetbrains.plugins.scala.lang.psi.types.Nothing
  override def calcType() : ScType = Nothing //todo remove me
}