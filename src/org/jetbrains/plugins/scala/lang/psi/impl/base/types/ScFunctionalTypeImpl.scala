package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import api.base.types._
import psi.ScalaPsiElementImpl
import lang.psi.types._
import com.intellij.lang.ASTNode
import result.{Failure, Success, TypingContext}

/**
 * @author ilyas, Alexander Podkhalyuzin
 */

class ScFunctionalTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScFunctionalTypeElement {
  override def toString: String = "FunctionalType"

  override def getType(ctx: TypingContext) = {
    val returnTypeRes = wrap(returnTypeElement).flatMap(_.getType(ctx))

    paramTypeElement match {
      case tup: ScTupleTypeElement =>
        val comps = tup.components.map(_.getType(ctx))
        val result = Success(ScFunctionType(returnTypeRes.unwrap(Any), comps.map(_.unwrap(Nothing))), Some(this))
        (for (f@Failure(_, _) <- Seq(returnTypeRes) ++ comps) yield f).foldLeft(result)(_.apply(_))
      case other: ScTypeElement => {
        val oType = other.getType(ctx)
        val paramTypes = oType.unwrap(Any) match {
          case Unit => Seq.empty
          case t => Seq(t)
        }
        val result = Success(ScFunctionType(returnTypeRes.unwrap(Any), paramTypes), Some(this))
        (for (f@Failure(_, _) <- Seq(returnTypeRes) ++ paramTypes) yield f).foldLeft(result)(_.apply(_))
      }
    }
  }
}