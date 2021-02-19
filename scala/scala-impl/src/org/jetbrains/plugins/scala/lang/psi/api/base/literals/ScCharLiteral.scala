package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package literals

import org.jetbrains.plugins.scala.lang.psi.api._


trait ScCharLiteralBase extends ScLiteralBase { this: ScCharLiteral =>
  override protected type V = Character
}

abstract class ScCharLiteralCompanion {

  def unapply(literal: ScCharLiteral): Option[Char] =
    Option(literal.getValue).map(_.charValue) // DO NOT REMOVE MAPPING
}