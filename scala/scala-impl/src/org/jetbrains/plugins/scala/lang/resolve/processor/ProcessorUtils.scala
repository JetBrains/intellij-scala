package org.jetbrains.plugins.scala.lang.resolve.processor

import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets.{METHOD, VAR}

private[lang] object ProcessorUtils {

  def shouldProcessOnlyStable(processor: PsiScopeProcessor): Boolean =
    processor match {
      case BaseProcessor(kinds) =>
        !kinds.contains(METHOD) && !kinds.contains(VAR)
      case _ =>
        false
    }
}
