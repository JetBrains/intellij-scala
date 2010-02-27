package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import psi.api.toplevel.imports.usages.ImportUsed

object ScalaResolveResult {
  def empty = new ScalaResolveResult(null, ScSubstitutor.empty, Set[ImportUsed]())

  def unapply(r: ScalaResolveResult): Some[(PsiNamedElement, ScSubstitutor)] = Some(r.element, r.substitutor)
}

class ScalaResolveResult(val element: PsiNamedElement,
                         val substitutor: ScSubstitutor = ScSubstitutor.empty,
                         val importsUsed: collection.Set[ImportUsed] = collection.Set[ImportUsed](),
                         val nameShadow: Option[String] = None,
                         val implicitConversionClass: Option[PsiClass] = None) extends ResolveResult {

  def getElement() = element

  def isApplicable() = true

  def isAccessible() = true

  def isValidResult() = isAccessible && isApplicable

  def isCyclicReference = false

  def isRenamed: Option[String] = nameShadow

  //In valid program we should not have two resolve results with the same element but different substitutor,
  // so factor by element
  override def equals(other: Any): Boolean = other match {
    case rr: ScalaResolveResult => element eq rr.element
    case _ => false
  }

  override def hashCode: Int = element.hashCode
}
