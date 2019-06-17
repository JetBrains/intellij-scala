package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package literals

trait ScFloatLiteral extends ScLiteral.Numeric {
  override protected type V = java.lang.Float

  override private[psi] type T = Float
}

object ScFloatLiteral extends ScLiteral.NumericCompanion[ScFloatLiteral]