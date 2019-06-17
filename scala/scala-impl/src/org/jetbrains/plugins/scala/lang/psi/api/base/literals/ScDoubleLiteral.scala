package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package literals

trait ScDoubleLiteral extends ScLiteral.Numeric {
  override protected type V = java.lang.Double

  override private[psi] type T = Double
}

object ScDoubleLiteral extends ScLiteral.NumericCompanion[ScDoubleLiteral]