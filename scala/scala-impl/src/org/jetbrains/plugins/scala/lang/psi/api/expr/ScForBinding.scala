package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor

trait ScForBinding extends ScEnumerator with ScPatternedEnumerator {

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = visitor.visitForBinding(this)
}

object ScForBinding {
  object expr {
    def unapply(forBinding: ScForBinding): Option[ScExpression] = forBinding.expr
  }
}