package org.jetbrains.plugins.scala.lang.psi.api.base
package types

trait ScLiteralTypeElement extends ScTypeElement {
  override protected val typeName = "LiteralType"

  def getLiteral: ScLiteral
}
