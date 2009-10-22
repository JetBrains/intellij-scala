package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import psi.types.{ScTupleType, ScType}
import psi.types.result.{TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.Any

/**
 * @author ilyas, Alexander Podkhalyuzin
 */

class ScTupleTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTupleTypeElement {
  override def toString: String = "TupleType"

  override def getType(ctx: TypingContext) = collectFailures(components.map(_.getType(ctx)), Any)(ScTupleType(_))
}