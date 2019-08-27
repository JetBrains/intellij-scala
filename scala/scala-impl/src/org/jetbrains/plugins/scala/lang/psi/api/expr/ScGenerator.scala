package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern

trait ScGenerator extends ScEnumerator with ScPatternedEnumerator {

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitGenerator(this)
  }
}

object ScGenerator {
  def unapply(gen: ScGenerator): Option[(ScPattern, Option[ScExpression])] = Some(gen.pattern -> gen.expr)
}