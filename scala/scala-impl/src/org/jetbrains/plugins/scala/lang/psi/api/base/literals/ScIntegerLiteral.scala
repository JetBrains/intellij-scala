package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package literals

import org.jetbrains.plugins.scala.lang.psi.api._


trait ScIntegerLiteralBase extends ScLiteral.Numeric { this: ScIntegerLiteral =>
  override protected type V = Integer

  override private[psi] type T = Int
}

abstract class ScIntegerLiteralCompanion extends ScLiteral.NumericCompanion[ScIntegerLiteral]