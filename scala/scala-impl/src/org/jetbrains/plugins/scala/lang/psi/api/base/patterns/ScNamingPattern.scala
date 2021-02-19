package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api._


/**
* @author Alexander Podkhalyuzin
*/

trait ScNamingPatternBase extends ScBindingPatternBase { this: ScNamingPattern =>
  def named: ScPattern = findChild[ScPattern].get
}