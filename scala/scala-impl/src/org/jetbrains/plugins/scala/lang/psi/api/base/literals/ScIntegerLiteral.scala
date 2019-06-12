package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package literals

trait ScIntegerLiteral extends ScLiteral {
  override protected type V = Integer
}

object ScIntegerLiteral {

  def unapply(literal: ScIntegerLiteral): Option[Int] =
    Option(literal.getValue).map(_.intValue) // DO NOT REMOVE MAPPING
}
