package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package literals

import org.jetbrains.plugins.scala.lang.psi.api._


trait ScFloatLiteralBase extends ScLiteral.Numeric { this: ScFloatLiteral =>
  override protected type V = java.lang.Float

  override private[psi] type T = Float
}

abstract class ScFloatLiteralCompanion extends ScLiteral.NumericCompanion[ScFloatLiteral]