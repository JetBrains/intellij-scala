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
import psi.types.result.{Failure, Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.Any

/**
 * @author Alexander Podkhalyuzin
 */

class ScTupleTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTupleTypeElement {
  override def toString: String = "TupleType"

  override def getType(ctx: TypingContext) = {
    val comps = components.map(_.getType(ctx))
    val result = ScTupleType(comps.map {
      case Success(t, _) => t
      case Failure(_, _) => Any
    })
    (for (f@Failure(_, _) <- comps) yield f).foldLeft(Success(result, Some(this)))(_.apply(_))
  }
}