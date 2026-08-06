package org.jetbrains.plugins.scala.lang.psi.api.base
package literals

trait ScIntegerLiteral extends ScLiteral.Numeric {
  override protected type V = Integer

  override private[psi] type T = Int
}

object ScIntegerLiteral extends ScLiteral.NumericCompanion[ScIntegerLiteral]
