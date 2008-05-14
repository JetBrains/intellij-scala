package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Patterns, introduced by case classes or extractors
*/

trait ScConstructorPattern extends ScPattern {

  def args: ScPatternArgumentList

}