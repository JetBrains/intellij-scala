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
import api.ScalaElementVisitor
import com.intellij.psi.PsiElementVisitor

/**
 * @author ilyas, Alexander Podkhalyuzin
 */

class ScFunctionalTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScFunctionalTypeElement {
  override def toString: String = "FunctionalType"

  protected def innerType(ctx: TypingContext) = {
    val returnTypeRes = wrap(returnTypeElement).flatMap(_.getType(ctx))

    val paramTypes = paramTypeElement match {
      case tup: ScTupleTypeElement =>
        tup.components.map(_.getType(ctx)).map(_.getOrNothing)
      case par: ScParenthesisedTypeElement if par.typeElement == None => Seq.empty
      case other => {
        val oType = other.getType(ctx)
        Seq(oType.getOrAny)
      }
    }
    val funType = new ScFunctionType(returnTypeRes.getOrAny, paramTypes)(getProject, getResolveScope)
    val result = Success(funType, Some(this))
    (for (f@Failure(_, _) <- Seq(returnTypeRes) ++ paramTypes) yield f).foldLeft(result)(_.apply(_))
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