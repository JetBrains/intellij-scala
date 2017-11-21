package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api.base.{ScPathElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
trait ScThisReference extends ScExpression with ScPathElement {

  def reference: Option[ScStableCodeReferenceElement] =
    findChild(classOf[ScStableCodeReferenceElement])

  def refTemplate: Option[ScTemplateDefinition]

  override def accept(visitor: ScalaElementVisitor): Unit = {
    visitor.visitThisReference(this)
  }
}