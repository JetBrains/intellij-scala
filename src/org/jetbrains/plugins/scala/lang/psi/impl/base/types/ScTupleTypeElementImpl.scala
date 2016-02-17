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
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

/**
 * @author ilyas, Alexander Podkhalyuzin
 */

class ScTupleTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTupleTypeElement {
  @Cached(synchronized = true, ModCount.getBlockModificationCount, this)
  def desugarizedInfixType: Option[ScParameterizedTypeElement] = {
    val n = components.length
    val newTypeText = s"_root_.scala.Tuple$n[${components.map(_.getText).mkString(", ")}]"
    val newTypeElement = ScalaPsiElementFactory.createTypeElementFromText(newTypeText, getContext, this)
    newTypeElement match {
      case p: ScParameterizedTypeElement => Some(p)
      case _ => None
    }
  }

  protected def innerType(ctx: TypingContext): TypeResult[ScType] = {
    desugarizedInfixType match {
      case Some(p) => p.getType(ctx)
      case _ => Failure("Cannot desugarize infix type", Some(this))
    }
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitTupleTypeElement(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitTupleTypeElement(this)
      case _ => super.accept(visitor)
    }
  }
}