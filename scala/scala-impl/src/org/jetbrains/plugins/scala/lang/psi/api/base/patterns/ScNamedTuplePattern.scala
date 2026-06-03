package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor

trait ScNamedTuplePattern extends ScPattern {
  final def components: Seq[ScNamedTuplePatternComponent] = findChildren[ScNamedTuplePatternComponent]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitNamedTuplePattern(this)
  }
}


object ScNamedTuplePattern {
  def unapply(pattern: ScNamedTuplePattern): Some[Seq[ScNamedTuplePatternComponent]] =
    Some(pattern.components)
}
