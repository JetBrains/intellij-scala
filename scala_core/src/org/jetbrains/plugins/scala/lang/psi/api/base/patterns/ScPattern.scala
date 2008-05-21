package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi._

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

trait ScPattern extends ScalaPsiElement {

  def bindings: Seq[ScBindingPattern]

  def subpatterns : Seq[ScPattern]
}