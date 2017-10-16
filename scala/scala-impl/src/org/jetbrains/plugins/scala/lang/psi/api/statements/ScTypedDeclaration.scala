package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable.TypingContext

trait ScTypedDeclaration extends ScDeclaration {
  def typeElement: Option[ScTypeElement]

  def getType(ctx: TypingContext.type): TypeResult[ScType]

  def declaredElements: Seq[ScTypedDefinition]
}