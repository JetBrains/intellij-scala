package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types


import api.statements.{ScTypeAliasDeclaration, ScTypeAliasDefinition, ScTypeAlias}
import api.toplevel.ScNamedElement
import com.intellij.psi.impl.PsiManagerEx

import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.util.IncorrectOperationException
import lexer.ScalaTokenTypes
import psi.ScalaPsiElementImpl
import api.base.types._
import psi.types._

import com.intellij.lang.ASTNode
import lang.resolve._
import processor.{CompletionProcessor, ResolveProcessor, BaseProcessor}
import result.{TypeResult, Failure, Success, TypingContext}
import com.intellij.psi._

/**
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

class ScTypeProjectionImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScTypeProjection {
  override def toString: String = "TypeProjection"

  protected def innerType(ctx: TypingContext) = {
    def lift(t: ScType) = Success(t, Some(this))
    bind match {
      case Some(ScalaResolveResult(elem, subst)) => {
        val te: TypeResult[ScType] = typeElement.getType(ctx)
        te match {
          case Success(ScDesignatorType(pack: PsiPackage), a) => {
            Success(ScDesignatorType(elem), Some(this))
          }
          case _ =>
            te map {ScProjectionType(_, elem, subst)}
        }
      }
      case _ => Failure("Cannot Resolve reference", Some(this))
    }
  }

  def getKinds(incomplete: Boolean) = StdKinds.stableClass

  def multiResolve(incomplete: Boolean) =
    getManager.asInstanceOf[PsiManagerEx].getResolveCache.resolveWithCaching(this, MyResolver, true, incomplete)
  def getVariants: Array[Object] = {
    _resolve(new CompletionProcessor(getKinds(true))).map(
      r => {
        r match {
          case res: ScalaResolveResult => ResolveUtils.getLookupElement(res)
          case _ => r.getElement
        }
      }
    )
  }

  def bindToElement(p1: PsiElement) = throw new IncorrectOperationException("NYI")
  def nameId = findChildByType(ScalaTokenTypes.tIDENTIFIER)
  def qualifier = None

  object MyResolver extends ResolveCache.PolyVariantResolver[ScTypeProjectionImpl] {
    def resolve(projection: ScTypeProjectionImpl, incomplete: Boolean) = {
      projection._resolve(new ResolveProcessor(projection.getKinds(incomplete), projection, projection.refName))
    }
  }

  private def _resolve(proc : BaseProcessor) = {
    val projected = typeElement.getType(TypingContext.empty).getOrElse(Any)
    proc.processType(projected, this)
    proc.candidates.map {r : ScalaResolveResult =>
      r.element match {
        case mem: PsiMember if mem.getContainingClass != null =>
          new ScalaResolveResult(mem, r.substitutor/*.bindO(mem.getContainingClass, projected, this)*/, r.importsUsed)
        case _ => r : ResolveResult
      }
    }
  }

  def getSameNameVariants: Array[ResolveResult] = _resolve(new CompletionProcessor(getKinds(true), false, Some(refName)))
}