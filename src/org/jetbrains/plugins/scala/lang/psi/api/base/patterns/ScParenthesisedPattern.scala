package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
*/

trait ScParenthesisedPattern extends ScPattern {
  def subpattern = findChild(classOf[ScPattern])
}