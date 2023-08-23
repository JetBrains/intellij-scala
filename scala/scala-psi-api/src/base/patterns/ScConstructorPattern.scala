package org.jetbrains.plugins.scala.lang.psi.api.base
package patterns

trait ScConstructorPattern extends ScExtractorPattern {
  def args: ScPatternArgumentList = findChild[ScPatternArgumentList].get
  override def ref: ScStableCodeReference = findChild[ScStableCodeReference].get
}

object ScConstructorPattern {
  def unapply(pattern: ScConstructorPattern): Option[(ScStableCodeReference, ScPatternArgumentList)] =
    Some((pattern.ref, pattern.args))
}