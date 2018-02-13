package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.LookupElementManager
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScReferenceElementImpl
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve._
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, CompletionProcessor, ResolveProcessor}
import org.jetbrains.plugins.scala.macroAnnotations.{CachedWithRecursionGuard, ModCount}

/**
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/
class ScTypeProjectionImpl(node: ASTNode) extends ScReferenceElementImpl(node) with ScTypeProjection {
  protected def innerType: TypeResult = {
    this.bind() match {
      case Some(ScalaResolveResult(elem, _)) =>
        typeElement.`type`().map {
          case ScDesignatorType(_: PsiPackage) => ScalaType.designator(elem)
          case t => ScProjectionType(t, elem)
        }
      case _ => Failure("Cannot Resolve reference")
    }
  }

  def getKinds(incomplete: Boolean, completion: Boolean): ResolveTargets.ValueSet = StdKinds.stableClass

  @CachedWithRecursionGuard(this, ScalaResolveResult.EMPTY_ARRAY, ModCount.getBlockModificationCount)
  def multiResolveScala(incomplete: Boolean): Array[ScalaResolveResult] =
    doResolve(new ResolveProcessor(getKinds(incomplete), ScTypeProjectionImpl.this, refName))

  def getVariants: Array[Object] = {
    val isInImport: Boolean = this.parentOfType(classOf[ScImportStmt], strict = false).isDefined
    doResolve(new CompletionProcessor(getKinds(incomplete = true), this)).flatMap { res =>
        import org.jetbrains.plugins.scala.lang.psi.types.api.Nothing
        val qualifier = res.fromType.getOrElse(Nothing)
        LookupElementManager.getLookupElement(res, isInImport = isInImport, qualifierType = qualifier, isInStableCodeReference = false)
    }
  }

  def bindToElement(p1: PsiElement) = throw new IncorrectOperationException("NYI")
  def nameId: PsiElement = findChildByType[PsiElement](ScalaTokenTypes.tIDENTIFIER)
  def qualifier = None

  def doResolve(processor: BaseProcessor, accessibilityCheck: Boolean = true): Array[ScalaResolveResult] = {
    if (!accessibilityCheck) processor.doNotCheckAccessibility()
    val projected = typeElement.`type`().getOrAny
    processor.processType(projected, this)
    val res = processor.candidates.map { r =>
      r.element match {
        case mem: PsiMember if mem.containingClass != null =>
          new ScalaResolveResult(mem, r.substitutor, r.importsUsed)
        case _ => r
      }
    }
    if (accessibilityCheck && res.length == 0) return doResolve(processor, accessibilityCheck = false)
    res
  }

  def getSameNameVariants: Array[ScalaResolveResult] = doResolve(new CompletionProcessor(getKinds(incomplete = true), this) {

    override protected val forName = Some(refName)
  })

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
