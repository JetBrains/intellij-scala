package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cachedWithRecursionGuard}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base._

trait ResolvableStableCodeReference extends ScalaPsiElement with ScPolyResolvable

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
        val importResolverNoMethods = new StableCodeReferenceResolver(stableRef, ResolveMode.Full, false, false) {
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
        val importResolverNoTypes = new StableCodeReferenceResolver(stableRef, ResolveMode.Full, false, false) {
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
        val noConstructorResolver = new StableCodeReferenceResolver(stableRef, ResolveMode.Full, false, true)
        noConstructorResolver.resolve()
      }

    /**
     * @param mode how the arguments of the constructor invocation are checked, see [[ResolveMode]]
     */
    def resolveAllConstructors(mode: ResolveMode = ResolveMode.Full): Array[ScalaResolveResult] =
      cachedWithRecursionGuard(
        "ResolveAllConstructors",
        stableRef,
        ScalaResolveResult.EMPTY_ARRAY,
        BlockModificationTracker(stableRef),
        Tuple1(mode)
      ) {
        ProgressManager.checkCanceled()
        val resolverAllConstructors = new StableCodeReferenceResolver(stableRef, mode, true, false)
        resolverAllConstructors.resolve()
      }

    /**
     * @param mode how the arguments of the constructor invocation (if any) are checked, see [[ResolveMode]].
     *             [[ResolveMode.Full]] is the same as `multiResolveScala(incomplete = false)`
     */
    def resolveWithMode(mode: ResolveMode): Array[ScalaResolveResult] = mode match {
      case ResolveMode.Full => stableRef.multiResolveScala(incomplete = false)
      case _ =>
        cachedWithRecursionGuard(
          "ResolvableStableCodeReference.Ext.resolveWithMode",
          stableRef,
          ScalaResolveResult.EMPTY_ARRAY,
          BlockModificationTracker(stableRef),
          Tuple1(mode)
        ) {
          ProgressManager.checkCanceled()
          val resolver = new StableCodeReferenceResolver(stableRef, mode, false, false)
          resolver.resolve()
        }
    }
  }
}
