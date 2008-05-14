package org.jetbrains.plugins.scala.lang.psi.api.base

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import api.base.patterns._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScPatternList extends ScalaPsiElement {
  def patterns: Seq[ScPattern]
}