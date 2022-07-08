package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScReferenceImpl
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve._
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, CompletionProcessor, ResolveProcessor}
import org.jetbrains.plugins.scala.macroAnnotations.CachedWithRecursionGuard

class ScTypeProjectionImpl(node: ASTNode) extends ScReferenceImpl(node) with ScTypeProjection {
  override protected def innerType: TypeResult = {
    this.bind() match {
      case Some(ScalaResolveResult(elem, _)) =>
        typeElement.`type`().map {
          case ScDesignatorType(_: PsiPackage) => ScalaType.designator(elem)
          case t => ScProjectionType(t, elem)
        }
      case _ => Failure(ScalaBundle.message("cannot.resolve.reference"))
    }
  }

  override def getKinds(incomplete: Boolean, completion: Boolean): ResolveTargets.ValueSet = StdKinds.stableClass

  @CachedWithRecursionGuard(this, ScalaResolveResult.EMPTY_ARRAY, BlockModificationTracker(this))
  override def multiResolveScala(incomplete: Boolean): Array[ScalaResolveResult] =
    doResolve(new ResolveProcessor(getKinds(incomplete), ScTypeProjectionImpl.this, refName))

  override def bindToElement(p1: PsiElement) = throw new IncorrectOperationException("NYI")
  override def nameId: PsiElement = findChildByType[PsiElement](ScalaTokenTypes.tIDENTIFIER)
  override def qualifier: Option[ScalaPsiElement] = None

  override def doResolve(processor: BaseProcessor, accessibilityCheck: Boolean = true): Array[ScalaResolveResult] = {
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

  override def getSameNameVariants: Array[ScalaResolveResult] = doResolve(new CompletionProcessor(getKinds(incomplete = true), this) {

    override protected val forName: Option[String] = Some(refName)
  })

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitTypeProjection(this)
  }
}
