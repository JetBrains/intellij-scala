package org.jetbrains.plugins.scala.lang.psi.api.base
package literals

trait ScDoubleLiteral extends ScFloatingPointLiteral {
  override protected type V = java.lang.Double

  override private[psi] type T = Double
}

object ScDoubleLiteral extends ScFloatingPointLiteral.Companion[ScDoubleLiteral]