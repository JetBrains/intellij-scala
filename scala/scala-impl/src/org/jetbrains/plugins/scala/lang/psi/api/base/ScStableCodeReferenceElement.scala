package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolvableStableCodeReferenceElement, ScalaResolveResult}

trait ScStableCodeReferenceElement extends ScReferenceElement with ResolvableStableCodeReferenceElement with ScPathElement {
  def qualifier: Option[ScStableCodeReferenceElement] =
    getFirstChild match {case s: ScStableCodeReferenceElement => Some(s) case _ => None}

  def pathQualifier: Option[ScPathElement] = getFirstChild match {case s: ScPathElement => Some(s) case _ => None}

  def qualName: String = {
    qualifier match {
      case Some(x) => x.qualName + "." + refName
      case _ => refName
    }
  }

  def isConstructorReference: Boolean
  def getConstructor: Option[ScConstructor]

  def getResolveResultVariants: Array[ScalaResolveResult]

  protected def processQualifier(processor: BaseProcessor): Array[ScalaResolveResult]

  def doResolve(processor: BaseProcessor, accessibilityCheck: Boolean = true): Array[ScalaResolveResult]
}

object ScStableCodeReferenceElement {
  object withQualifier {
    def unapply(ref: ScStableCodeReferenceElement): Option[ScStableCodeReferenceElement] = ref.qualifier
  }
}
