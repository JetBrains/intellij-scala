package org.jetbrains.plugins.scala.lang.psi.impl.base.types


import _root_.scala.collection.Set


import com.intellij.psi.impl.PsiManagerEx

import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.{PsiMember, ResolveResult, PsiElement}
import com.intellij.util.IncorrectOperationException
import lexer.ScalaTokenTypes
import psi.ScalaPsiElementImpl
import api.base.types._
import psi.types._

import com.intellij.lang.ASTNode
import resolve._

/**
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

class ScTypeProjectionImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScTypeProjection{
  override def toString: String = "TypeProjection"

  override def getType() = new ScProjectionType(typeElement.getType, this)

  def getKinds(incomplete: Boolean) = StdKinds.stableClass

  def multiResolve(incomplete: Boolean) =
    getManager.asInstanceOf[PsiManagerEx].getResolveCache.resolveWithCaching(this, MyResolver, false, incomplete)
  def getVariants: Array[Object] = _resolve(new CompletionProcessor(getKinds(true))).map(r => r.getElement)

  def bindToElement(p1: PsiElement) = throw new IncorrectOperationException("NYI")
  def nameId = findChildByType(ScalaTokenTypes.tIDENTIFIER)
  def qualifier = None

  object MyResolver extends ResolveCache.PolyVariantResolver[ScTypeProjectionImpl] {
    def resolve(projection: ScTypeProjectionImpl, incomplete: Boolean) = {
      projection._resolve(new ResolveProcessor(projection.getKinds(incomplete), projection.refName))
    }
  }

  private def _resolve(proc : BaseProcessor) = {
    val projected = typeElement.getType
    proc.processType(projected, this)
    proc.candidates.map {r : ScalaResolveResult =>
      r.element match {
        case mem: PsiMember if mem.getContainingClass != null =>
          new ScalaResolveResult(mem, r.substitutor.bindOuter(mem.getContainingClass, projected, this))
        case _ => r : ResolveResult
      }
    }
  }
}