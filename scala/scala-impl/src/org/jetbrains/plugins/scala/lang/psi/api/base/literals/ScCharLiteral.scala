package org.jetbrains.plugins.scala.lang.psi.api.base
package literals

trait ScCharLiteral extends ScLiteral {
  override protected type V = Character

  override final def isSimpleLiteral: Boolean = true
}

object ScCharLiteral {

  def unapply(literal: ScCharLiteral): Option[Char] =
    Option(literal.getValue).map(_.charValue) // DO NOT REMOVE MAPPING
}
