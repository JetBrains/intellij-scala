package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.LookupElementManager
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.resolve._
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, CompletionProcessor, ResolveProcessor}
import org.jetbrains.plugins.scala.macroAnnotations.{CachedMappedWithRecursionGuard, ModCount}

/**
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/
class ScTypeProjectionImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScTypeProjection {
  protected def innerType(ctx: TypingContext): TypeResult[ScType] = {
    this.bind() match {
      case Some(ScalaResolveResult(elem, _)) =>
        val te: TypeResult[ScType] = typeElement.getType(ctx)
        te match {
          case Success(ScDesignatorType(_: PsiPackage), _) =>
            this.success(ScalaType.designator(elem))
          case _ =>
            te map {ScProjectionType(_, elem, superReference = false)}
        }
      case _ => Failure("Cannot Resolve reference", Some(this))
    }
  }

  def getKinds(incomplete: Boolean, completion: Boolean): ResolveTargets.ValueSet = StdKinds.stableClass

  @CachedMappedWithRecursionGuard(this, Array.empty, ModCount.getBlockModificationCount)
  def multiResolve(incomplete: Boolean): Array[ResolveResult] =
    doResolve(new ResolveProcessor(getKinds(incomplete), ScTypeProjectionImpl.this, refName))

  def getVariants: Array[Object] = {
    allVariantsCached.flatMap {
      case res: ScalaResolveResult =>
        import org.jetbrains.plugins.scala.lang.psi.types.api.Nothing
        val isInImport: Boolean = ScalaPsiUtil.getParentOfType(this, classOf[ScImportStmt]) != null
        val qualifier = res.fromType.getOrElse(Nothing)
        LookupElementManager.getLookupElement(res, isInImport = isInImport, qualifierType = qualifier, isInStableCodeReference = false)
      case r => Seq(r.getElement)
    }
  }

  def bindToElement(p1: PsiElement) = throw new IncorrectOperationException("NYI")
  def nameId: PsiElement = findChildByType[PsiElement](ScalaTokenTypes.tIDENTIFIER)
  def qualifier = None

  def doResolve(processor: BaseProcessor, accessibilityCheck: Boolean = true): Array[ResolveResult] = {
    if (!accessibilityCheck) processor.doNotCheckAccessibility()
    val projected = typeElement.getType(TypingContext.empty).getOrAny
    processor.processType(projected, this)
    val res = processor.candidates.map { r : ScalaResolveResult =>
      r.element match {
        case mem: PsiMember if mem.containingClass != null =>
          new ScalaResolveResult(mem, r.substitutor/*.bindO(mem.getContainingClass, projected, this)*/, r.importsUsed)
        case _ => r : ResolveResult
      }
    }
    if (accessibilityCheck && res.length == 0) return doResolve(processor, accessibilityCheck = false)
    res
  }

  def getSameNameVariants: Array[ResolveResult] = allVariantsCached.collect {
    case rr @ ResolveResultEx(named: PsiNamedElement) if named.name == refName => rr
  }

  @CachedMappedWithRecursionGuard(this, Array.empty, CachesUtil.enclosingModificationOwner(this))
  private def allVariantsCached: Array[ResolveResult] = {
    doResolve(new CompletionProcessor(getKinds(incomplete = true), ScTypeProjectionImpl.this))
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitTypeProjection(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitTypeProjection(this)
      case _ => super.accept(visitor)
    }
  }
}
