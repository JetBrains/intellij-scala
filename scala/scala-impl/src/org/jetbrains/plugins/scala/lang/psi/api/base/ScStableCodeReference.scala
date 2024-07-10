package org.jetbrains.plugins.scala.lang.psi.api.base

import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolvableStableCodeReference, ScalaResolveResult}

import scala.annotation.tailrec

trait ScStableCodeReference extends ScReference with ResolvableStableCodeReference with ScPathElement {

  override def qualifier: Option[ScStableCodeReference] = getFirstChild.asOptionOf[ScStableCodeReference]

  final def deepestQualifier: ScStableCodeReference = ScStableCodeReference.deepestQualifier(this)

  def pathQualifier: Option[ScPathElement]              = getFirstChild.asOptionOf[ScPathElement]

  def qualName: String =
    qualifier match {
      case Some(x) => x.qualName + "." + refName
      case _       => refName
    }

  def isConstructorReference: Boolean
  def getConstructorInvocation: Option[ScConstructorInvocation]

  def getResolveResultVariants: Array[ScalaResolveResult]

  def doResolve(processor: BaseProcessor, accessibilityCheck: Boolean = true): Array[ScalaResolveResult]
}

object ScStableCodeReference {
  object withQualifier {
    def unapply(ref: ScStableCodeReference): Option[ScStableCodeReference] = ref.qualifier
  }

  @tailrec
  private def deepestQualifier(ref: ScStableCodeReference): ScStableCodeReference = {
    ref.qualifier match {
      case Some(q) => deepestQualifier(q)
      case None => ref
    }
  }
}
