package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import psi.api.toplevel.imports.usages.ImportUsed

object ScalaResolveResult {
  def empty = new ScalaResolveResult(null, ScSubstitutor.empty, Set[ImportUsed]())

  def unapply(r: ScalaResolveResult) = Some(r.element, r.substitutor)
}

class ScalaResolveResult(val element: PsiNamedElement,
                         val substitutor: ScSubstitutor,
                         val importsUsed: _root_.scala.collection.Set[ImportUsed]) extends ResolveResult {
  def this(element: PsiNamedElement, substitutor: ScSubstitutor) = this(element, substitutor, Set[ImportUsed]())
  def this(element: PsiNamedElement) = this (element, ScSubstitutor.empty, Set[ImportUsed]())

  def getElement() = element

  def isValidResult() = true

  def isCyclicReference = false

  //In valid program we should not have two resolve results with the same element but different substitutor,
  // so factor by element
  override def equals(other: Any): Boolean = other match {
    case rr: ScalaResolveResult => element eq rr.element
    case _ => false
  }

  override def hashCode: Int = element.hashCode
}
