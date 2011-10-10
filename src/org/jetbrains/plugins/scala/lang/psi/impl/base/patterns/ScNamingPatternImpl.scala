package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import api.base.patterns._
import lexer.ScalaTokenTypes
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._
import psi.types.result.{TypeResult, Failure, Success, TypingContext}
import scope.PsiScopeProcessor
import api.ScalaElementVisitor
import psi.types.ScType

/**
 * @author Alexander Podkhalyuzin
 */

class ScNamingPatternImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScNamingPattern {
  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "NamingPattern"

  def nameId = findChildByType(TokenSets.ID_SET)

  def isWildcard: Boolean = findChildByType(ScalaTokenTypes.tUNDER) != null

  override def getType(ctx: TypingContext): TypeResult[ScType] = {
    if (getLastChild.isInstanceOf[ScSeqWildcard]) {
      return expectedType match {
        case Some(x) => Success(x, Some(this))
        case _ =>  Failure("No expected type for wildcard naming", Some(this))
      }
    }
    if (named == null) Failure("Cannot infer type", Some(this))
    else named.getType(ctx)
  }

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement,
                                   place: PsiElement) = {
    if (isStable) {
      ScalaPsiUtil.processImportLastParent(processor, state, place, lastParent, getType(TypingContext.empty))
    } else true
  }

  override def getOriginalElement: PsiElement = super[ScNamingPattern].getOriginalElement
}