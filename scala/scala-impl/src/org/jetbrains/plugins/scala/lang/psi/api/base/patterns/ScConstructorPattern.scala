package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

trait ScConstructorPattern extends ScPattern {
  def args: ScPatternArgumentList = findChild[ScPatternArgumentList].get
  def ref: ScStableCodeReference = findChild[ScStableCodeReference].get
}

object ScConstructorPattern {
  def unapply(pattern: ScConstructorPattern): Option[(ScStableCodeReference, ScPatternArgumentList)] =
    Some((pattern.ref, pattern.args))
}