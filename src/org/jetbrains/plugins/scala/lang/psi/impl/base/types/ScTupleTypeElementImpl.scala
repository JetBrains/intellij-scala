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
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.Any
import api.ScalaElementVisitor
import com.intellij.psi.PsiElementVisitor

/**
 * @author ilyas, Alexander Podkhalyuzin
 */

class ScTupleTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTupleTypeElement {
  override def toString: String = "TupleType: " + getText

  private var desugarizedTypeModCount: Long = 0L
  private var desugarizedType: Option[ScParameterizedTypeElement] = null

  def desugarizedInfixType: Option[ScParameterizedTypeElement] = {
    def inner(): Option[ScParameterizedTypeElement] = {
      val n = components.length
      val newTypeText = s"_root_.scala.Tuple$n[${components.map(_.getText).mkString(", ")}]"
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
        visitor.visitTupleTypeElement(this)
      }

      override def accept(visitor: PsiElementVisitor) {
        visitor match {
          case s: ScalaElementVisitor => s.visitTupleTypeElement(this)
          case _ => super.accept(visitor)
        }
      }
}