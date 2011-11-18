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
import api.ScalaElementVisitor
import api.toplevel.imports.ScImportStmt
import caches.ScalaRecursionManager
import com.intellij.openapi.util.Computable

/**
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

class ScTypeProjectionImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScTypeProjection {
  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "TypeProjection"

  protected def innerType(ctx: TypingContext) = {
    def lift(t: ScType) = Success(t, Some(this))
    bind match {
      case Some(ScalaResolveResult(elem, subst)) => {
        val te: TypeResult[ScType] = typeElement.getType(ctx)
        te match {
          case Success(ScDesignatorType(pack: PsiPackage), a) => {
            Success(ScType.designator(elem), Some(this))
          }
          case _ =>
            te map {ScProjectionType(_, elem, subst)}
        }
      }
      case _ => Failure("Cannot Resolve reference", Some(this))
    }
  }

  def getKinds(incomplete: Boolean, completion: Boolean) = StdKinds.stableClass

  def multiResolve(incomplete: Boolean) =
    getManager.asInstanceOf[PsiManagerEx].getResolveCache.resolveWithCaching(this, MyResolver, true, incomplete)

  def getVariants: Array[Object] = {
    val isInImport: Boolean = ScalaPsiUtil.getParentOfType(this, classOf[ScImportStmt]) != null
    doResolve(new CompletionProcessor(getKinds(true))).flatMap {
      case res: ScalaResolveResult =>
        import org.jetbrains.plugins.scala.lang.psi.types.Nothing
        val qualifier = res.fromType.getOrElse(Nothing)
        ResolveUtils.getLookupElement(res, isInImport = isInImport, qualifierType = qualifier, isInStableCodeReference = false)
      case r => Seq(r.getElement)
    }
  }

  def bindToElement(p1: PsiElement) = throw new IncorrectOperationException("NYI")
  def nameId = findChildByType(ScalaTokenTypes.tIDENTIFIER)
  def qualifier = None

  object MyResolver extends ResolveCache.PolyVariantResolver[ScTypeProjectionImpl] {
    def resolve(projection: ScTypeProjectionImpl, incomplete: Boolean) = {
      projection.doResolve(new ResolveProcessor(projection.getKinds(incomplete), projection, projection.refName))
    }
  }

  def doResolve(proc: BaseProcessor) = {
    val projected = typeElement.getType(TypingContext.empty).getOrAny
    proc.processType(projected, this)
    proc.candidates.map {r : ScalaResolveResult =>
      r.element match {
        case mem: PsiMember if mem.getContainingClass != null =>
          new ScalaResolveResult(mem, r.substitutor/*.bindO(mem.getContainingClass, projected, this)*/, r.importsUsed)
        case _ => r : ResolveResult
      }
    }
  }

  def getSameNameVariants: Array[ResolveResult] = doResolve(new CompletionProcessor(getKinds(true), false, Some(refName)))
}