package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package literals

trait ScLongLiteral extends ScLiteral {
  override protected type V = java.lang.Long
}

object ScLongLiteral {

  def unapply(literal: ScLongLiteral): Option[Long] =
    Option(literal.getValue).map(_.longValue) // DO NOT REMOVE MAPPING
}
