package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

class ScalaResolveResult(val element : PsiNamedElement, val substitutor : ScSubstitutor) extends ResolveResult  {
  def this(element : PsiNamedElement) = this(element, ScSubstitutor.empty)

  def getElement() = element

  def isValidResult() = true
}