package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import com.intellij.psi.ResolveResult
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

trait ScStableCodeReferenceElement extends ScReferenceElement with ScPathElement {
  def qualifier: Option[ScStableCodeReferenceElement] =
    getFirstChild match {case s: ScStableCodeReferenceElement => Some(s) case _ => None}
  def pathQualifier = getFirstChild match {case s: ScPathElement => Some(s) case _ => None}

  def qualName: String = {
    qualifier match {
      case Some(x) => x.qualName + "." + refName
      case _ => refName
    }
  }

  def isConstructorReference: Boolean
  def getConstructor: Option[ScConstructor]

  def resolveNoConstructor: Array[ResolveResult]
  def resolveAllConstructors: Array[ResolveResult]
  def shapeResolve: Array[ResolveResult]
  def shapeResolveConstr: Array[ResolveResult]

  def getResolveResultVariants: Array[ScalaResolveResult]
}

object ScStableCodeReferenceElement {
  object withQualifier {
    def unapply(ref: ScStableCodeReferenceElement): Option[ScStableCodeReferenceElement] = ref.qualifier
  }
}
