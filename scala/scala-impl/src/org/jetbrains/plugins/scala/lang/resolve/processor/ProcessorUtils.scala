package org.jetbrains.plugins.scala.lang.resolve.processor

import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets

private[lang] object ProcessorUtils {

  /**
   * NOTE: there is some inconsistency in naming and logic in different resolution scenarios.
   * This method returns `false` (telling that we can also process non-stable elements) if kinds contain METHOD(`def`) or `var`.
   * However even identifiers referencing to `def` and `var` definitions can be stable in scala 3.<br>
   * See [[ResolveTargets.HAS_STABLE_TYPE]] for the details.
   * This resolve target isn't handled here just because it was enough for the purpose of highlighting to show
   * "Stable identifier required but ... found" error.
   * On the same time handling of [[ResolveTargets.HAS_STABLE_TYPE]] lead to many test failures.
   */
  def shouldProcessOnlyStable(processor: PsiScopeProcessor): Boolean =
    processor match {
      case BaseProcessor(kinds) =>
        import ResolveTargets._
        val isDefinitelyNotStable = kinds.contains(METHOD) || kinds.contains(VAR)
        !isDefinitelyNotStable
      case _ =>
        false
    }
}
