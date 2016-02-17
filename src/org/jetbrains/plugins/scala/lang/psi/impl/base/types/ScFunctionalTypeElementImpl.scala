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
 * @author ilyas, Alexander Podkhalyuzin
 */

class ScFunctionalTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScFunctionalTypeElement {
  @Cached(synchronized = true, ModCount.getBlockModificationCount, this)
  def desugarizedInfixType: Option[ScParameterizedTypeElement] = {
    val paramTypes = paramTypeElement match {
      case tup: ScTupleTypeElement => tup.components
      case par: ScParenthesisedTypeElement if par.typeElement.isEmpty => Seq.empty
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