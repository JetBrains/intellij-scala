package org.jetbrains.plugins.scala.codeInsight.daemon

import com.intellij.codeInsight.daemon.ChangeLocalityDetector
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.Tracing
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

class ScalaChangeLocalityDetector extends ChangeLocalityDetector {

  override def getChangeHighlightingDirtyScopeFor(changedElement: PsiElement): PsiElement = {
    changedElement.getParent match {
      case expr: ScExpression if BlockModificationTracker.hasStableType(expr) =>
        Tracing.locality(expr)

        expr
      case _ => null
    }
  }
}
