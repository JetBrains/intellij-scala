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
import org.jetbrains.plugins.scala.lang.psi.types.result.{TypeResult, Failure, Success, TypingContext}
import api.ScalaElementVisitor
import com.intellij.psi.PsiElementVisitor

/**
 * @author ilyas, Alexander Podkhalyuzin
 */

class ScFunctionalTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScFunctionalTypeElement {
  override def toString: String = "FunctionalType: " + getText

  private var desugarizedTypeModCount: Long = 0L
  private var desugarizedType: Option[ScParameterizedTypeElement] = null

  def desugarizedInfixType: Option[ScParameterizedTypeElement] = {
    def inner(): Option[ScParameterizedTypeElement] = {
      val paramTypes = paramTypeElement match {
        case tup: ScTupleTypeElement => tup.components
        case par: ScParenthesisedTypeElement if par.typeElement == None => Seq.empty
        case other => Seq(other)
      }
      val n = paramTypes.length
      val newTypeText = s"_root_.scala.Function$n[${paramTypes.map(_.getText).mkString(",")}${if (n == 0) "" else ", "}" +
              s"${returnTypeElement.map(_.getText).getOrElse("Any")}]"
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
      case _ => Failure("Cannot desugarize function type", Some(this))
    }
  }

    override def accept(visitor: ScalaElementVisitor) {
        visitor.visitFunctionalTypeElement(this)
      }

      override def accept(visitor: PsiElementVisitor) {
        visitor match {
          case s: ScalaElementVisitor => s.visitFunctionalTypeElement(this)
          case _ => super.accept(visitor)
        }
      }
}