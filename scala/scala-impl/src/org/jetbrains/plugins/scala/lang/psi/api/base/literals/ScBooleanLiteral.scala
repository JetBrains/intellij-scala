package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package literals

import org.jetbrains.plugins.scala.lang.psi.api._


trait ScBooleanLiteralBase extends ScLiteralBase { this: ScBooleanLiteral =>
  override protected type V = java.lang.Boolean
}

abstract class ScBooleanLiteralCompanion {

  def unapply(literal: ScBooleanLiteral): Some[Boolean] =
    Some(literal.getValue)
}