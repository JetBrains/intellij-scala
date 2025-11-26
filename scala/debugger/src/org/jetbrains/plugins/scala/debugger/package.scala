package org.jetbrains.plugins.scala

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.incremental.Highlighting.builtInHighlightingDisabledIn

package object debugger {
  /**
   * [[com.intellij.platform.debugger.impl.frontend.FrontendEditorLinesBreakpointsInfoManager]] pre-computes possible breakpoint types,
   * even if there are no breakpoints set, far beyond the visible area, which triggers type inference and interferes with incremental highlighting.
   *
   * @see [[org.jetbrains.plugins.scala.debugger.breakpoints.ScalaLineBreakpointType.computeVariants]]
   */
  private[debugger] def typeAware(e: PsiElement): Boolean =
    !(builtInHighlightingDisabledIn(e.getProject) || incremental.Highlighting.enabledIn(e.getProject))
}
