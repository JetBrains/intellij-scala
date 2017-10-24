package org.jetbrains.plugins.scala
package lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

/**
 * @author Alefas
 * @since 26/09/14.
 */
trait ScTypeVariableTypeElement extends ScTypeElement with ScNamedElement {
  override protected val typeName = "TypeVariable"

  override def accept(visitor: ScalaElementVisitor): Unit = visitor.visitTypeVariableTypeElement(this)
}
