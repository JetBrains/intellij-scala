package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import com.intellij.psi.ResolveResult

trait ScStableCodeReferenceElement extends ScReferenceElement with ScPathElement {
  def qualifier: Option[ScStableCodeReferenceElement] =
    getFirstChild match {case s: ScStableCodeReferenceElement => Some(s) case _ => None}
  def pathQualifier = getFirstChild match {case s: ScPathElement => Some(s) case _ => None}

  def qualName: String = {
    val builder = new StringBuilder
    def inner(s: ScStableCodeReferenceElement) {
      builder.insert(0, s.refName)
      s.qualifier match {
        case Some(x) => {
          builder.insert(0, ".")
          inner(x)
        }
        case None =>
      }
    }
    builder.toString
  }

  def isConstructorReference: Boolean
  def getConstructor: Option[ScConstructor]

  def shapeResolve: Array[ResolveResult]
}
