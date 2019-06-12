package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package literals

trait ScFloatLiteral extends ScLiteral {
  override protected type V = java.lang.Float
}

object ScFloatLiteral {

  def unapply(literal: ScFloatLiteral): Option[Float] =
    Option(literal.getValue).map(_.floatValue) // DO NOT REMOVE MAPPING
}