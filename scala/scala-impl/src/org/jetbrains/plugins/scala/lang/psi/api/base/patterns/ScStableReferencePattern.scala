package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api._


import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression

trait ScStableReferencePatternBase extends ScPatternBase { this: ScStableReferencePattern =>
  def referenceExpression: Option[ScReferenceExpression]
}

abstract class ScStableReferencePatternCompanion {

  def unapply(pattern: ScStableReferencePattern): Option[ScReferenceExpression] = pattern.referenceExpression
}