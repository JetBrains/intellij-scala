package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.psi.api._


trait ScLiteralTypeElementBase extends ScTypeElementBase { this: ScLiteralTypeElement =>
  override protected val typeName = "LiteralType"

  def getLiteral: ScLiteral
}