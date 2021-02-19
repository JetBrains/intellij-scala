package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api._


/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/
trait ScLiteralPatternBase extends ScPatternBase { this: ScLiteralPattern =>
  def getLiteral: ScLiteral = findChild[ScLiteral].get
}

abstract class ScLiteralPatternCompanion {

  def unapply(pattern: ScLiteralPattern): Option[ScLiteral] =
    Option(pattern.getLiteral)
}