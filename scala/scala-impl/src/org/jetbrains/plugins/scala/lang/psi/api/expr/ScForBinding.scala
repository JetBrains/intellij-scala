package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api._


trait ScForBindingBase extends ScEnumeratorBase with ScPatternedEnumeratorBase { this: ScForBinding =>

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = visitor.visitForBinding(this)
}

abstract class ScForBindingCompanion {
  object expr {
    def unapply(forBinding: ScForBinding): Option[ScExpression] = forBinding.expr
  }
}