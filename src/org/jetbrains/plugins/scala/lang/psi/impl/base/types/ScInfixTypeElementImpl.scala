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
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

/**
 * @author Alexander Podkhalyuzin, ilyas
 */

class ScInfixTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScInfixTypeElement {
  def rOp = findChildrenByClass(classOf[ScTypeElement]) match {
    case Array(_, r) => Some(r)
    case _ => None
  }

  @Cached(synchronized = true, ModCount.getBlockModificationCount, this)
  def desugarizedInfixType: Option[ScParameterizedTypeElement] = {
    val newTypeText = s"${ref.getText}[${lOp.getText}, ${rOp.map(_.getText).getOrElse("Nothing")}}]"
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
    visitor.visitInfixTypeElement(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitInfixTypeElement(this)
      case _ => super.accept(visitor)
    }
  }
}