package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package literals

import org.jetbrains.plugins.scala.lang.psi.api._


trait ScLongLiteralBase extends ScLiteral.Numeric { this: ScLongLiteral =>
  override protected type V = java.lang.Long

  override private[psi] type T = Long
}

abstract class ScLongLiteralCompanion extends ScLiteral.NumericCompanion[ScLongLiteral]