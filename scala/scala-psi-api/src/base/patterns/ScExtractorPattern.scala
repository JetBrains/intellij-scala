package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference

/**
 * Base trait for patterns, which resolve to unapply/unapplySeq methods
 */
trait ScExtractorPattern extends ScPattern {
  def ref: ScStableCodeReference
}
