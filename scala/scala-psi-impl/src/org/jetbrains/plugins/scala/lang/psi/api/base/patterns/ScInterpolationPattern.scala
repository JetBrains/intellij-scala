package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolated

trait ScInterpolationPattern extends ScExtractorPattern with ScInterpolated {
  def args: ScPatternArgumentList = findChild[ScPatternArgumentList].get
}
