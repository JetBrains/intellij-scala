package org.jetbrains.plugins.scala
package lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.api._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScNamedElementBase}

/**
 * @author Alefas
 * @since 26/09/14.
 */
trait ScTypeVariableTypeElementBase extends ScTypeElementBase with ScNamedElementBase { this: ScTypeVariableTypeElement =>
  override protected val typeName = "TypeVariable"

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = visitor.visitTypeVariableTypeElement(this)
}