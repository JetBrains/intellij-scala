package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import psi.api.toplevel.imports.usages.ImportUsed
import psi.api.statements.{ScFunctionDefinition, ScFunction}

object ScalaResolveResult {
  def empty = new ScalaResolveResult(null, ScSubstitutor.empty, Set[ImportUsed]())

  def unapply(r: ScalaResolveResult): Some[(PsiNamedElement, ScSubstitutor)] = Some(r.element, r.substitutor)
}

class ScalaResolveResult(val element: PsiNamedElement,
                         val substitutor: ScSubstitutor = ScSubstitutor.empty,
                         val importsUsed: collection.Set[ImportUsed] = collection.Set[ImportUsed](),
                         val nameShadow: Option[String] = None,
                         val implicitConversionClass: Option[PsiClass] = None,
                         val problems: Seq[ApplicabilityProblem] = Seq.empty,
                         val boundClass: PsiClass = null,
                         val implicitFunction: Option[PsiNamedElement] = None,
                         val implicitType: Option[ScType] = None,
                         val isHacked: Boolean = false,
                         val defaultParameterUsed: Boolean = false,
                         val innerResolveResult: Option[ScalaResolveResult] = None,
                         val parentElement: Option[PsiNamedElement] = None) extends ResolveResult {

  def getElement = element

  /**
   * this is important to get precedence information
   */
  def getActualElement = {
    parentElement match {
      case Some(e) => e
      case None => element
    }
  }

  def isApplicable: Boolean = problems.isEmpty

  def isApplicableInternal: Boolean = {
    innerResolveResult match {
      case Some(r) => r.isApplicable
      case None => isApplicable
    }
  }

  def isAccessible = true

  def isValidResult = isAccessible && isApplicable

  def isCyclicReference = false

  def isRenamed: Option[String] = nameShadow

  def copy(subst: ScSubstitutor = substitutor, problems: Seq[ApplicabilityProblem] = problems,
           defaultParameterUsed: Boolean = defaultParameterUsed,
           innerResolveResult: Option[ScalaResolveResult] = innerResolveResult): ScalaResolveResult =
    new ScalaResolveResult(element, subst, importsUsed, nameShadow, implicitConversionClass, problems, boundClass,
      implicitFunction, implicitType, isHacked, defaultParameterUsed, innerResolveResult, parentElement)

  //In valid program we should not have two resolve results with the same element but different substitutor,
  // so factor by element
  override def equals(other: Any): Boolean = other match {
    case rr: ScalaResolveResult => element eq rr.element
    case _ => false
  }

  override def hashCode: Int = element.hashCode
}
