package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}

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

  override def toString: String = "NamingPattern: " + name

  def nameId = findChildByType[PsiElement](TokenSets.ID_SET)

  def isWildcard: Boolean = findChildByType[PsiElement](ScalaTokenTypes.tUNDER) != null

  override def getType(ctx: TypingContext): TypeResult[ScType] = {
    if (getLastChild.isInstanceOf[ScSeqWildcard]) {
      return expectedType match {
        case Some(x) => Success(x, Some(this))
        case _ =>  Failure("No expected type for wildcard naming", Some(this))
      }
    }
    if (named == null) Failure("Cannot infer type", Some(this))
    else {
      expectedType match {
        case Some(expectedType) => named.getType(TypingContext.empty).map(expectedType.glb(_))
        case  _ => named.getType(ctx)
      }
    }
  }

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement,
                                   place: PsiElement) = {
    if (isStable) {
      ScalaPsiUtil.processImportLastParent(processor, state, place, lastParent, getType(TypingContext.empty))
    } else true
  }

  override def getOriginalElement: PsiElement = super[ScNamingPattern].getOriginalElement
}