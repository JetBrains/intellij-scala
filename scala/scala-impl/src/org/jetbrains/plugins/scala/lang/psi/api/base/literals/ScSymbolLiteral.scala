package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package literals

import org.jetbrains.plugins.scala.lang.psi.api._


trait ScSymbolLiteralBase extends ScLiteralBase { this: ScSymbolLiteral =>
  override protected type V = Symbol
}

abstract class ScSymbolLiteralCompanion {

  def unapply(literal: ScSymbolLiteral): Option[Symbol] =
    Option(literal.getValue)
}