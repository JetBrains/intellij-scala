package org.jetbrains.plugins.scala.lang.resolve.processor

import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets.{HAS_STABLE_TYPE_MARKER, METHOD, VAR}

private[lang] object ProcessorUtils {

  def shouldProcessOnlyStable(processor: PsiScopeProcessor): Boolean =
    processor match {
      case BaseProcessor(kinds) =>
        kinds.contains(HAS_STABLE_TYPE_MARKER) || // see HAS_STABLE_TYPE_MARKER docs
          !kinds.contains(METHOD) && !kinds.contains(VAR) // left this implementation, just not to break some old behavior
      case _ =>
        false
    }
}
