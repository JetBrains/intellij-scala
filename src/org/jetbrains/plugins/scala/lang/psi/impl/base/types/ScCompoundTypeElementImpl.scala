package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.base.types._
import scala.Some
import psi.types.result.TypingContext
import psi.types.{ScSubstitutor, ScCompoundType}
import api.ScalaElementVisitor
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.lang.psi.types

/**
 * @author Alexander Podkhalyuzin
 */

class ScCompoundTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScCompoundTypeElement {
  override def toString: String = "CompoundType: " + getText

  protected def innerType(ctx: TypingContext) = {
    val comps = components.map(_.getType(ctx))
    refinement match {
      case None => collectFailures(comps, types.Any)(new ScCompoundType(_, Seq.empty, Seq.empty, ScSubstitutor.empty))
      case Some(r) => collectFailures(comps, types.Any)(new ScCompoundType(_, r.holders.toList, r.types.toList, ScSubstitutor.empty))
    }
  }

    override def accept(visitor: ScalaElementVisitor) {
        visitor.visitCompoundTypeElement(this)
      }

      override def accept(visitor: PsiElementVisitor) {
        visitor match {
          case s: ScalaElementVisitor => s.visitCompoundTypeElement(this)
          case _ => super.accept(visitor)
        }
      }
}