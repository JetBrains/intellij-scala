package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.api._


import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern

/**
 * @author Alexander Podkhalyuzin
 */

trait ScPatternedBase extends ScalaPsiElementBase { this: ScPatterned =>
  def pattern: ScPattern
}