package org.jetbrains.plugins.scala.lang.psi.api.base.types

trait ScLiteralTypeElement extends ScTypeElement {
  override protected val typeName = "LiteralType"

  def getLiteralText: String
}
