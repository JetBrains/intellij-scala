package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api.base.{ScPathElement, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

trait ScThisReference extends ScExpression with ScPathElement {

  def reference: Option[ScStableCodeReference] =
    findChild[ScStableCodeReference]

  def refTemplate: Option[ScTemplateDefinition]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitThisReference(this)
  }
}