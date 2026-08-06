package org.jetbrains.plugins.scala.lang.psi.api.base
package patterns

trait ScConstructorPattern extends ScExtractorPattern {
  def args: ScPatternArgumentList = findChild[ScPatternArgumentList].get
}

object ScConstructorPattern {
  def unapply(pattern: ScConstructorPattern): Some[(ScStableCodeReference, ScPatternArgumentList)] =
    Some((pattern.ref, pattern.args))
}