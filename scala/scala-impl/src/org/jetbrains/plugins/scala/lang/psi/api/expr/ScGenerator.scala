package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api._


import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern

trait ScGeneratorBase extends ScEnumeratorBase with ScPatternedEnumeratorBase { this: ScGenerator =>

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitGenerator(this)
  }
}

abstract class ScGeneratorCompanion {
  def unapply(gen: ScGenerator): Option[(ScPattern, Option[ScExpression])] = Some(gen.pattern -> gen.expr)
}