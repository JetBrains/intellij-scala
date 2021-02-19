package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api._


/**
* @author Alexander Podkhalyuzin
* Patterns, introduced by case classes or extractors
*/
trait ScConstructorPatternBase extends ScPatternBase { this: ScConstructorPattern =>
  def args: ScPatternArgumentList = findChild[ScPatternArgumentList].get
  def ref: ScStableCodeReference = findChild[ScStableCodeReference].get
}

abstract class ScConstructorPatternCompanion {
  def unapply(pattern: ScConstructorPattern): Option[(ScStableCodeReference, ScPatternArgumentList)] =
    Some((pattern.ref, pattern.args))
}