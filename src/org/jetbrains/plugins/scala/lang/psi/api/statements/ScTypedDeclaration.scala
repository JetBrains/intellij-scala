package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import base.types.ScTypeElement
import toplevel.ScTypedDefinition
import types.result.{TypeResult, TypingContext}
import types.ScType

trait ScTypedDeclaration extends ScDeclaration {
  def typeElement : Option[ScTypeElement]

  def getType(ctx: TypingContext) : TypeResult[ScType]

  def declaredElements : Seq[ScTypedDefinition]
}