package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import lang.lexer._
import com.intellij.psi._
import scope.PsiScopeProcessor
import api.ScalaElementVisitor
import psi.types.ScType
import psi.types.result.{TypeResult, Failure, Success, TypingContext}

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScTypedPatternImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypedPattern {
  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  def nameId = findChildByType(TokenSets.ID_SET)

  def isWildcard: Boolean = findChildByType(ScalaTokenTypes.tUNDER) != null

  override def isIrrefutableFor(t: Option[ScType]): Boolean = {
    t match {
      case Some(t) => getType(TypingContext.empty) match {
        case Success(tp, _) if t conforms tp => true
        case _ => false
      }
      case _ => false
    }
  }

  override def toString: String = "TypedPattern"

  override def getType(ctx: TypingContext): TypeResult[ScType] = {
    typePattern match {
      case Some(tp) =>
        if (tp.typeElement == null) return Failure("No type element for type pattern", Some(this))
        val typeElementType = tp.typeElement.getType(ctx)
        expectedType match {
          case Some(expectedType) =>
            typeElementType.map {
              case typeElementType =>
                if (expectedType.conforms(typeElementType)) expectedType
                else typeElementType //todo:
            }
          case _ =>
            typeElementType
        }
      case None => Failure("No type pattern", Some(this))
    }
  }

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement,
                                   place: PsiElement) = {
    ScalaPsiUtil.processImportLastParent(processor, state, place, lastParent, getType(TypingContext.empty))
  }

  override def getOriginalElement: PsiElement = super[ScTypedPattern].getOriginalElement
}