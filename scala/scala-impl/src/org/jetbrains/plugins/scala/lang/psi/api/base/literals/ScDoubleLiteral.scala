package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package literals

import org.jetbrains.plugins.scala.lang.psi.api._


trait ScDoubleLiteralBase extends ScLiteral.Numeric { this: ScDoubleLiteral =>
  override protected type V = java.lang.Double

  override private[psi] type T = Double
}

abstract class ScDoubleLiteralCompanion extends ScLiteral.NumericCompanion[ScDoubleLiteral]