package org.jetbrains.plugins.scala.lang.psi.api.base
package literals

trait ScFloatLiteral extends ScFloatingPointLiteral {
  override protected type V = java.lang.Float

  override private[psi] type T = Float
}

object ScFloatLiteral extends ScFloatingPointLiteral.Companion[ScFloatLiteral]