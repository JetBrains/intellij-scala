package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScForBinding extends ScEnumerator with ScPatterned {
  def expr: Option[ScExpression]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = visitor.visitForBinding(this)
}

object ScForBinding {
  object expr {
    def unapply(forBinding: ScForBinding): Option[ScExpression] = forBinding.expr
  }
}