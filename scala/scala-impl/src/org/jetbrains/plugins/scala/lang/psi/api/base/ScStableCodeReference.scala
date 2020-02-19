package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolvableStableCodeReference, ScalaResolveResult}

trait ScStableCodeReference extends ScReference with ResolvableStableCodeReference with ScPathElement {
  override def qualifier: Option[ScStableCodeReference] =
    getFirstChild match {case s: ScStableCodeReference => Some(s) case _ => None}

  def pathQualifier: Option[ScPathElement] = getFirstChild match {case s: ScPathElement => Some(s) case _ => None}

  def qualName: String = {
    qualifier match {
      case Some(x) => x.qualName + "." + refName
      case _ => refName
    }
  }

  def isConstructorReference: Boolean
  def getConstructorInvocation: Option[ScConstructorInvocation]

  def getResolveResultVariants: Array[ScalaResolveResult]

  protected def processQualifier(processor: BaseProcessor): Array[ScalaResolveResult]

  def doResolve(processor: BaseProcessor, accessibilityCheck: Boolean = true): Array[ScalaResolveResult]
}

object ScStableCodeReference {
  object withQualifier {
    def unapply(ref: ScStableCodeReference): Option[ScStableCodeReference] = ref.qualifier
  }
}
