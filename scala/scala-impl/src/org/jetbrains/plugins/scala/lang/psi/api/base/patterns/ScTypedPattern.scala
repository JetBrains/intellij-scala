package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api._


import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

/**
 * @author Alexander Podkhalyuzin
 */
trait ScTypedPatternBase extends ScBindingPatternBase { this: ScTypedPattern =>
  def typePattern: Option[ScTypePattern] = findChild[ScTypePattern]
}

abstract class ScTypedPatternCompanion {
  def unapply(pattern: ScTypedPattern): Option[ScTypeElement] =
    pattern.typePattern.map(_.typeElement)
}