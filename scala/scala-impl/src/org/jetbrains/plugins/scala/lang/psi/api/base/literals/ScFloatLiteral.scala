package org.jetbrains.plugins.scala.lang.psi.api.base
package literals

trait ScFloatLiteral extends ScLiteral.Numeric {
  override protected type V = java.lang.Float

  override private[psi] type T = Float
}

object ScFloatLiteral extends ScLiteral.NumericCompanion[ScFloatLiteral]