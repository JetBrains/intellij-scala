package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import collection.Set
import com.intellij.lang.ASTNode
import api.base.types._
import org.jetbrains.plugins.scala.lang.psi.types.ScCompoundType
import org.jetbrains.plugins.scala.lang.psi.types.Any
import scala.Some
import psi.types.result.{Failure, Success, TypingContext}

/**
 * @author Alexander Podkhalyuzin
 */

class ScCompoundTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScCompoundTypeElement {
  override def toString: String = "CompoundType"

  override def getType(ctx: TypingContext) = {
    val comps = components.map(_.getType(ctx))
    refinement match {
      case None => collectFailures(comps, Any)(new ScCompoundType(_, Seq.empty, Seq.empty))
      case Some(r) => collectFailures(comps, Any)(new ScCompoundType(_, r.holders.toList, r.types.toList))
    }
  }
}