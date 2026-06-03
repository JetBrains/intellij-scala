package org.jetbrains.plugins.scala.lang.psi.api.base
package literals

trait ScLongLiteral extends ScLiteral.Numeric {
  override protected type V = java.lang.Long

  override private[psi] type T = Long
}

object ScLongLiteral extends ScLiteral.NumericCompanion[ScLongLiteral]
