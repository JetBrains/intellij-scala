package org.jetbrains.plugins.scala.lang.psi.api.base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

trait ScPatterns extends ScalaPsiElement {
  def patterns: Seq[ScPattern]
}

object ScPatterns {
  def unapply(e: ScPatterns): Some[Seq[ScPattern]] = Some(e.patterns)
}