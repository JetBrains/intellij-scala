package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package literals

trait ScDoubleLiteral extends ScLiteral {
  override protected type V = java.lang.Double
}

object ScDoubleLiteral {

  def unapply(literal: ScDoubleLiteral): Option[Double] =
    Option(literal.getValue).map(_.doubleValue) // DO NOT REMOVE MAPPING
}