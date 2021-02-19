package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api._


import org.jetbrains.plugins.scala.extensions.ObjectExt

/** 
* @author Alexander Podkhalyuzin
*/

trait ScParenthesisedPatternBase extends ScPatternBase with ScParenthesizedElementBase { this: ScParenthesisedPattern =>
  type Kind = ScPattern

  override def innerElement: Option[ScPattern] = findChild[ScPattern]

  override def sameTreeParent: Option[ScPattern] = getParent.asOptionOf[ScPattern]
}

abstract class ScParenthesisedPatternCompanion {
  def unapply(e: ScParenthesisedPattern): Option[ScPattern] = e.innerElement
}