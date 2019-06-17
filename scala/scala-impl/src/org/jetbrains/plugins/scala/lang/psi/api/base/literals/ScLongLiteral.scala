package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package literals

trait ScLongLiteral extends ScLiteral.Numeric {
  override protected type V = java.lang.Long

  override private[psi] type T = Long
}

object ScLongLiteral extends ScLiteral.NumericCompanion[ScLongLiteral]
