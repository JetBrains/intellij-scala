package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import psi.types.result.{TypeResult, TypingContext}
import psi.types.ScType

/**
 * Author: Alexander Podkhalyuzin
 * Date: 22.02.2008
 */
trait ScSimpleTypeElement extends ScTypeElement {

  def reference: Option[ScStableCodeReferenceElement] = findChild(classOf[ScStableCodeReferenceElement])
  def pathElement: ScPathElement = findChildByClassScala(classOf[ScPathElement])

  def singleton: Boolean

  def findConstructor: Option[ScConstructor]

  override def accept(visitor: ScalaElementVisitor) {visitor.visitSimpleTypeElement(this)}
}