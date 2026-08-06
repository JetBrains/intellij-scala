package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor

trait ScNamedTuple extends ScExpression {
  final def components: Seq[ScNamedTupleExprComponent] = findChildren[ScNamedTupleExprComponent]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitNamedTuple(this)
  }
}

object ScNamedTuple {
  def unapply(tuple: ScNamedTuple): Some[Seq[ScNamedTupleExprComponent]] =
    Some(tuple.components)
}
