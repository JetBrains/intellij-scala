package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

trait ScLiteralTypeElement extends ScTypeElement {
  override protected val typeName = "LiteralType"

  def getLiteral: ScLiteral
}
