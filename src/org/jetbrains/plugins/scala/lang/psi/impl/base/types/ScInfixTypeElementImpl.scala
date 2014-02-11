package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import psi.ScalaPsiElementImpl
import api.base.types._
import psi.types._
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult, TypingContext}
import api.ScalaElementVisitor
import com.intellij.psi.PsiElementVisitor
import api.statements.params.ScTypeParam

/**
 * @author Alexander Podkhalyuzin, ilyas
 */

class ScInfixTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScInfixTypeElement {
  override def toString: String = "InfixType"

  def rOp = findChildrenByClass(classOf[ScTypeElement]) match {
    case Array(_, r) => Some(r)
    case _ => None
  }

  private var desugarizedTypeModCount: Long = 0L
  private var desugarizedType: Option[ScParameterizedTypeElement] = null

  def desugarizedInfixType: Option[ScParameterizedTypeElement] = {
    def inner(): Option[ScParameterizedTypeElement] = {
      val newTypeText = s"${ref.getText}[${lOp.getText}, ${rOp.map(_.getText).getOrElse("Nothing")}}]"
      val newTypeElement = ScalaPsiElementFactory.createTypeElementFromText(newTypeText, getContext, this)
      newTypeElement match {
        case p: ScParameterizedTypeElement => Some(p)
        case _ => None
      }
    }

    synchronized {
      val currModCount = getManager.getModificationTracker.getModificationCount
      if (desugarizedType != null && desugarizedTypeModCount == currModCount) {
        return desugarizedType
      }
      desugarizedType = inner()
      desugarizedTypeModCount = currModCount
      return desugarizedType
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