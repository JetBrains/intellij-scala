package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements
package params

import org.jetbrains.plugins.scala.lang.psi.api._


/**
* @author Alexander Podkhalyuzin
* Date: 21.03.2008
*/

trait ScArgumentsBase extends ScalaPsiElementBase { this: ScArguments =>
  def getArgsCount: Int
}