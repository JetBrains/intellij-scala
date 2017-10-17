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
import org.jetbrains.plugins.scala.lang.psi.types.api.Any
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.{ScCompoundType, ScType, api}

/**
 * @author Alexander Podkhalyuzin
 */

class ScCompoundTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScCompoundTypeElement {
  protected def innerType: TypeResult[ScType] = {
    val comps = components.map(_.getType())
    refinement match {
      case None => collectFailures(comps, Any)(new ScCompoundType(_, Map.empty, Map.empty))
      case Some(r) => collectFailures(comps, api.Any)(ScCompoundType.fromPsi(_, r.holders.toList, r.types.toList))
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