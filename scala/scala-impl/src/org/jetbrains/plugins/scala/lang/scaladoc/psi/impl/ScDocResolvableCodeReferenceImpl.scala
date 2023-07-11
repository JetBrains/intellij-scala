package org.jetbrains.plugins.scala.lang.scaladoc.psi.impl

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cachedWithRecursionGuard}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceImpl
import org.jetbrains.plugins.scala.lang.resolve.StdKinds._
import org.jetbrains.plugins.scala.lang.resolve.processor.ResolveProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ScalaResolveResult}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocResolvableCodeReference

class ScDocResolvableCodeReferenceImpl(node: ASTNode) extends ScStableCodeReferenceImpl(node) with ScDocResolvableCodeReference {

  override protected def debugKind: Option[String] = Some("scalaDoc")

  //noinspection RedundantDefaultArgument
  override def multiResolveScala(incomplete: Boolean): Array[ScalaResolveResult] = cachedWithRecursionGuard("multiResolveScala", this, ScalaResolveResult.EMPTY_ARRAY, BlockModificationTracker(this), Tuple1(incomplete)) {
    multiResolveScalaImpl()
  }

  private def multiResolveScalaImpl(): Array[ScalaResolveResult] = {
    val ref = this

    val refName = ref.refName

    val referenceToObject = refName.endsWith("$")
    val refNameAdjusted = if (referenceToObject) refName.stripSuffix("$") else refName
    val kinds = stableImportSelector
    val processor = new ResolveProcessor(kinds, ref, refNameAdjusted)

    val resolveResult0: Array[ScalaResolveResult] =
      ref.doResolve(processor)

    //De-duplicating resolve results.
    //DETAILS: When we have a reference to a class method: [[org.MyClass.myMethod]] for a class with a companion object
    //qualifier `org.MyClass` is resolved to 2 targets: the class and it's companion object.
    //Then for every resolved qualifier `myMethod` is resolved to the same member of the class (yes, even for the object ¯\_(ツ)_/¯)
    //(search for `ScDocResolvableCodeReference` usages in `org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceImpl.processQualifier
    val resolveResult1 = resolveResult0.distinctBy(_.element)

    val resolveResult2 =
      if (referenceToObject && resolveResult1.forall(_.element.is[ScTypeDefinition])) // check if no methods were resolved
        resolveResult1.filter(_.element.is[ScObject])
      else
        resolveResult1

    //If we resolved to multiple references: class and it's companion, move companion object to the end
    //NOTE: it's not correct behaviour, but it will be incorrect until SCL-13263 is implemented
    //With this quick fix at least tests will be deterministic
    val resolveResult3 = resolveResult2.sortBy(r => if (r.element.is[ScObject]) 1 else 0)

    val resolveResult4 = resolveResult3.map {
      case ScalaResolveResult(constructor: ScPrimaryConstructor, _) if constructor.containingClass != null =>
        new ScalaResolveResult(constructor.containingClass)
      case result =>
        result
    }
    resolveResult4
  }

  override def getKinds(incomplete: Boolean, completion: Boolean): ResolveTargets.ValueSet =
    stableImportSelector
}