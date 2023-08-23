package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.plugins.scala.caches.{cachedWithRecursionGuard, BlockModificationTracker}
import org.jetbrains.plugins.scala.lang.psi.api.base._

trait ResolvableStableCodeReference

object ResolvableStableCodeReference {

  implicit class Ext(private val stableRef: ScStableCodeReference) extends AnyVal {

    def resolveTypesOnly(incomplete: Boolean): Array[ScalaResolveResult] =
      cachedWithRecursionGuard(
        "ResolvableStableCodeReference.Ext.resolveTypesOnly",
        stableRef,
        ScalaResolveResult.EMPTY_ARRAY,
        BlockModificationTracker(stableRef),
        Tuple1(incomplete)
      ) {
        val importResolverNoMethods = new StableCodeReferenceResolver(stableRef, false, false, false) {
          override protected def getKindsFor(ref: ScStableCodeReference): Set[ResolveTargets.Value] =
            ref.getKinds(incomplete = false).diff(StdKinds.methodRef)
        }
        importResolverNoMethods.resolve()
      }

    def resolveMethodsOnly(incomplete: Boolean): Array[ScalaResolveResult] =
      cachedWithRecursionGuard(
        "ResolvableStableCodeReference.Ext.resolveMethodsOnly",
        stableRef,
        ScalaResolveResult.EMPTY_ARRAY,
        BlockModificationTracker(stableRef),
        Tuple1(incomplete)
      ) {
        val importResolverNoTypes = new StableCodeReferenceResolver(stableRef, false, false, false) {
          override protected def getKindsFor(ref: ScStableCodeReference): Set[ResolveTargets.Value] =
            ref.getKinds(incomplete = false).diff(StdKinds.stableClass)
        }

        importResolverNoTypes.resolve()
      }

    def resolveNoConstructor: Array[ScalaResolveResult] =
      cachedWithRecursionGuard(
        "resolveNoConstructor",
        stableRef,
        ScalaResolveResult.EMPTY_ARRAY,
        BlockModificationTracker(stableRef)
      ) {
        ProgressManager.checkCanceled()
        val noConstructorResolver = new StableCodeReferenceResolver(stableRef, false, false, true)
        noConstructorResolver.resolve()
      }

    def resolveAllConstructors: Array[ScalaResolveResult] =
      cachedWithRecursionGuard(
        "ResolveAllConstructors",
        stableRef,
        ScalaResolveResult.EMPTY_ARRAY,
        BlockModificationTracker(stableRef)
      ) {
        ProgressManager.checkCanceled()
        val resolverAllConstructors = new StableCodeReferenceResolver(stableRef, false, true, false)
        resolverAllConstructors.resolve()
      }

    def shapeResolve: Array[ScalaResolveResult] =
      cachedWithRecursionGuard(
        "shapeResolve",
        stableRef,
        ScalaResolveResult.EMPTY_ARRAY,
        BlockModificationTracker(stableRef)
      ) {
        ProgressManager.checkCanceled()
        val shapesResolver = new StableCodeReferenceResolver(stableRef, true, false, false)
        shapesResolver.resolve()
      }

    def shapeResolveConstr: Array[ScalaResolveResult] =
      cachedWithRecursionGuard(
        "shareResolveConstr",
        stableRef,
        ScalaResolveResult.EMPTY_ARRAY,
        BlockModificationTracker(stableRef)
      ) {
        ProgressManager.checkCanceled()
        val shapesResolverAllConstructors = new StableCodeReferenceResolver(stableRef, true, true, false)
        shapesResolverAllConstructors.resolve()
      }
  }
}
