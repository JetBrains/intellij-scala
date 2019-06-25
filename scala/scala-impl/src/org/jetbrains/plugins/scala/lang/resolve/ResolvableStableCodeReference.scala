package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.macroAnnotations.{CachedWithRecursionGuard, ModCount}

import scala.collection.Set

trait ResolvableStableCodeReference

object ResolvableStableCodeReference {

  implicit class Ext(private val stableRef: ScStableCodeReference) extends AnyVal {

    @CachedWithRecursionGuard(stableRef, ScalaResolveResult.EMPTY_ARRAY, ModCount.getBlockModificationCount)
    def resolveTypesOnly(incomplete: Boolean): Array[ScalaResolveResult] = {
      val importResolverNoMethods = new StableCodeReferenceResolver(stableRef, false, false, false) {
        override protected def getKindsFor(ref: ScStableCodeReference): Set[ResolveTargets.Value] = {
          ref.getKinds(incomplete = false) -- StdKinds.methodRef
        }
      }
      importResolverNoMethods.resolve(stableRef, incomplete)
    }

    @CachedWithRecursionGuard(stableRef, ScalaResolveResult.EMPTY_ARRAY, ModCount.getBlockModificationCount)
    def resolveMethodsOnly(incomplete: Boolean): Array[ScalaResolveResult] = {
      val importResolverNoTypes = new StableCodeReferenceResolver(stableRef, false, false, false) {
        override protected def getKindsFor(ref: ScStableCodeReference): Set[ResolveTargets.Value] = {
          ref.getKinds(incomplete = false) -- StdKinds.stableClass
        }
      }

      importResolverNoTypes.resolve(stableRef, incomplete)
    }

    @CachedWithRecursionGuard(stableRef, ScalaResolveResult.EMPTY_ARRAY, ModCount.getBlockModificationCount)
    def resolveNoConstructor: Array[ScalaResolveResult] = {
      ProgressManager.checkCanceled()
      val noConstructorResolver = new StableCodeReferenceResolver(stableRef, false, false, true)
      noConstructorResolver.resolve(stableRef, incomplete = false)
    }

    @CachedWithRecursionGuard(stableRef, ScalaResolveResult.EMPTY_ARRAY, ModCount.getBlockModificationCount)
    def resolveAllConstructors: Array[ScalaResolveResult] = {
      ProgressManager.checkCanceled()
      val resolverAllConstructors = new StableCodeReferenceResolver(stableRef, false, true, false)
      resolverAllConstructors.resolve(stableRef, incomplete = false)
    }

    @CachedWithRecursionGuard(stableRef, ScalaResolveResult.EMPTY_ARRAY, ModCount.getBlockModificationCount)
    def shapeResolve: Array[ScalaResolveResult] = {
      ProgressManager.checkCanceled()
      val shapesResolver = new StableCodeReferenceResolver(stableRef, true, false, false)
      shapesResolver.resolve(stableRef, incomplete = false)
    }

    @CachedWithRecursionGuard(stableRef, ScalaResolveResult.EMPTY_ARRAY, ModCount.getBlockModificationCount)
    def shapeResolveConstr: Array[ScalaResolveResult] = {
      ProgressManager.checkCanceled()
      val shapesResolverAllConstructors = new StableCodeReferenceResolver(stableRef, true, true, false)
      shapesResolverAllConstructors.resolve(stableRef, incomplete = false)
    }
  }
}
