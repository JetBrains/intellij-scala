package org.jetbrains.plugins.scala.lang.scaladoc.psi.impl

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceImpl
import org.jetbrains.plugins.scala.lang.resolve.StdKinds._
import org.jetbrains.plugins.scala.lang.resolve.processor.ResolveProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ScalaResolveResult}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocResolvableCodeReference
import org.jetbrains.plugins.scala.macroAnnotations.CachedWithRecursionGuard

class ScDocResolvableCodeReferenceImpl(node: ASTNode) extends ScStableCodeReferenceImpl(node) with ScDocResolvableCodeReference {

  override protected def debugKind: Option[String] = Some("scalaDoc")

  //noinspection RedundantDefaultArgument
  @CachedWithRecursionGuard(this, ScalaResolveResult.EMPTY_ARRAY, BlockModificationTracker(this))
  override def multiResolveScala(incomplete: Boolean): Array[ScalaResolveResult] = {
    val ref = this

    val refName = ref.refName

    val referenceToObject = refName.endsWith("$")
    val (refNameAdjusted, kinds) =
      if (referenceToObject)
        (refName.stripSuffix("$"), stableImportSelector_WithoutClass)
      else
        (refName, stableImportSelector)

    val processor = new ResolveProcessor(kinds, ref, refNameAdjusted)
    val resolveResult0: Array[ScalaResolveResult] = ref.doResolve(processor)

    val resolveResult1: Array[ScalaResolveResult] =
      if (referenceToObject && resolveResult0.exists(_.element.is[ScObject]))
        resolveResult0.filter(_.element.is[ScObject])
      else
        resolveResult0

    resolveResult1.map {
      case ScalaResolveResult(constructor: ScPrimaryConstructor, _) if constructor.containingClass != null =>
        new ScalaResolveResult(constructor.containingClass)
      case result =>
        result
    }
  }

  override def getKinds(incomplete: Boolean, completion: Boolean): ResolveTargets.ValueSet =
    stableImportSelector
}