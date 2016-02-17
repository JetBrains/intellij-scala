package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.types
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.{ScCompoundType, ScSubstitutor}

/**
 * @author Alexander Podkhalyuzin
 */

class ScCompoundTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScCompoundTypeElement {
  protected def innerType(ctx: TypingContext) = {
    val comps = components.map(_.getType(ctx))
    refinement match {
      case None => collectFailures(comps, types.Any)(new ScCompoundType(_, Map.empty, Map.empty))
      case Some(r) => collectFailures(comps, types.Any)(ScCompoundType.fromPsi(_, r.holders.toList, r.types.toList, ScSubstitutor.empty))
    }
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitCompoundTypeElement(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => accept(s)
      case _ => super.accept(visitor)
    }
  }
}