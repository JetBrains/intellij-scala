package org.jetbrains.plugins.scala

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.incremental.Highlighting.builtInHighlightingDisabledIn

package object debugger {
  /**
   * [[com.intellij.platform.debugger.impl.frontend.FrontendEditorLinesBreakpointsInfoManager]] pre-computes possible breakpoint types,
   * even if there are no breakpoints set, far beyond the visible area, which triggers type inference and interferes with incremental highlighting. See IJPL-220984
   *
   * @see [[org.jetbrains.plugins.scala.debugger.breakpoints.ScalaLineBreakpointType.computeVariants]]
   */
  private[debugger] def typeAware(project: Project): Boolean =
    !(builtInHighlightingDisabledIn(project) || incremental.Highlighting.enabledIn(project))
}
