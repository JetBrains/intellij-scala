package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import com.intellij.psi.PsiElement

trait ScBindingPattern extends ScPattern with ScNamedElement {

  def isWildcard: Boolean

  def nameId : PsiElement
}