package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

trait ScForBinding extends ScEnumerator with ScPatternedEnumerator {

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = visitor.visitForBinding(this)
}

object ScForBinding {
  object expr {
    def unapply(forBinding: ScForBinding): Option[ScExpression] = forBinding.expr
  }
}