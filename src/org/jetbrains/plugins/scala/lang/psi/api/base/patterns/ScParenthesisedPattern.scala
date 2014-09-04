package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

/** 
* @author Alexander Podkhalyuzin
*/

trait ScParenthesisedPattern extends ScPattern {
  def subpattern = findChild(classOf[ScPattern])
}