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
import com.intellij.psi.{PsiNamedElement, PsiMember, ResolveResult, PsiElement}
import com.intellij.util.IncorrectOperationException
import lexer.ScalaTokenTypes
import psi.ScalaPsiElementImpl
import api.base.types._
import psi.types._

import com.intellij.lang.ASTNode
import resolve._
import result.{Success, TypingContext}

/**
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

class ScTypeProjectionImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScTypeProjection {
  override def toString: String = "TypeProjection"

  override def getType(ctx: TypingContext) = {
    def lift(t: ScType) = Success(t, Some(this))
    wrap(bind) flatMap {
      case ScalaResolveResult(alias: ScTypeAliasDefinition, s) =>
        if (alias.typeParameters == 0) alias.aliasedType(ctx) map {s.subst(_)}
        else lift(new ScTypeConstructorType(alias, s))
      case ScalaResolveResult(alias: ScTypeAliasDeclaration, s) => lift(new ScTypeAliasType(alias, s))
      case _ => typeElement.getType(ctx) map {ScProjectionType(_, this)}
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
      projection._resolve(new ResolveProcessor(projection.getKinds(incomplete), projection.refName))
    }
  }

  private def _resolve(proc : BaseProcessor) = {
    val projected = typeElement.cachedType.getOrElse(Any)
    proc.processType(projected, this)
    proc.candidates.map {r : ScalaResolveResult =>
      r.element match {
        case mem: PsiMember if mem.getContainingClass != null =>
          new ScalaResolveResult(mem, r.substitutor.bindO(mem.getContainingClass, projected, this), r.importsUsed)
        case _ => r : ResolveResult
      }
    }
  }

  def getSameNameVariants: Array[ResolveResult] = _resolve(new CompletionProcessor(getKinds(true))).
          filter(r => r.getElement.isInstanceOf[PsiNamedElement] &&
          r.getElement.asInstanceOf[PsiNamedElement].getName == refName)
}