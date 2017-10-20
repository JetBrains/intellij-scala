package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

/**
* @author Alexander Podkhalyuzin
*/

trait ScNamingPattern extends ScBindingPattern {
  def named: ScPattern = findChildByClassScala(classOf[ScPattern])
}