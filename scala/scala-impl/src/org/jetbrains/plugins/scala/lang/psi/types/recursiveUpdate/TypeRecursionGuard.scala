package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.plugins.scala.caches.RecursionManager

import java.util.concurrent.atomic.AtomicInteger

/**
 * Safety net for the type operations that rewrite this-types (`asSeenFrom`) and collapse singleton
 * paths. These are meant to terminate by construction, as scalac's do; a guard that trips marks a
 * divergence from scalac, not an expected case.
 *
 * In the IDE a trip degrades gracefully: the operation returns its input unchanged and a warning is
 * logged. In tests it fails hard, so that a new divergence is a test failure rather than a silently
 * different type. `-Dscala.types.recursionGuard.failHard=false` turns the hard failure off, e.g. to
 * highlight a whole corpus and count trips ([[trips]]).
 */
object TypeRecursionGuard {

  private val LOG = Logger.getInstance(getClass)

  /** Nesting depth of this-type substitution and singleton-path canonicalization beyond which they are
   *  taken to diverge. The scala/scala compiler and reflect sources nest a few levels deep. */
  val MaxSubstitutionDepth = 64

  private val tripCount = new AtomicInteger()

  /** The number of trips since the last [[resetTrips]]. */
  def trips: Int = tripCount.get

  def resetTrips(): Unit = tripCount.set(0)

  private def failHard: Boolean =
    sys.props.get("scala.types.recursionGuard.failHard") match {
      case Some(value) => value.toBoolean
      case None        => ApplicationManager.getApplication != null && ApplicationManager.getApplication.isUnitTestMode
    }

  /**
   * Records a trip of the guard `what`; throws when failing hard. Otherwise the caller returns a
   * fallback, so nothing computed from it in the enclosing guarded computation may be cached.
   */
  def tripped(what: String, detail: => String): Unit = {
    tripCount.incrementAndGet()
    RecursionManager.prohibitCaching()
    val message = s"type recursion guard tripped: $what: $detail"
    if (failHard) throw new TypeRecursionGuardException(message)
    else LOG.warn(message)
  }

  final class TypeRecursionGuardException(message: String) extends RuntimeException(message)

  private val substitutionDepth: ThreadLocal[Integer] = ThreadLocal.withInitial(() => 0)

  /**
   * Runs `body` one level deeper in nested this-type substitution or canonicalization; beyond
   * [[MaxSubstitutionDepth]] the guard trips and `fallback` is returned. A depth bound rather than a
   * keyed `RecursionGuard`, because a diverging substitution grows its types, so no key repeats. A trip
   * prohibits caching in the enclosing `RecursionManager` computations, so the fallback is not cached.
   */
  def nestedSubstitution[T](fallback: => T, detail: => String)(body: => T): T = {
    val depth = substitutionDepth.get
    if (depth >= MaxSubstitutionDepth) {
      tripped("substitution depth", detail)
      fallback
    } else {
      substitutionDepth.set(depth + 1)
      try body
      finally substitutionDepth.set(depth)
    }
  }
}
