package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern

/**
 * @author Alexander Podkhalyuzin
 */

trait ScPatterned extends ScalaPsiElement {
  def pattern: ScPattern
}