package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

object ScalaResolveResult {
  def empty = new ScalaResolveResult(null, ScSubstitutor.empty)

  def unapply(r : ScalaResolveResult) = Some(r.element, r.substitutor)
}

class ScalaResolveResult(val element : PsiNamedElement, val substitutor : ScSubstitutor) extends ResolveResult  {
  def this(element : PsiNamedElement) = this(element, ScSubstitutor.empty)

  def getElement() = element

  def isValidResult() = true

  def isCyclicReference = false

  //In valid program we should not have two resolve results with the same element but different substitutor,
  // so factor by element
  override def equals(other : Any): Boolean = other match {
    case rr : ScalaResolveResult => element eq rr.element
    case _ => false
  }

  override def hashCode: Int = element.hashCode
}
