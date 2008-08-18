package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

trait ScNamingPattern extends ScBindingPattern {
  def named = findChildByClass(classOf[ScPattern])
}