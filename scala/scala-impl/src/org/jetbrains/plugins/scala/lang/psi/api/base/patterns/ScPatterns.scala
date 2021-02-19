package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api._


/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

trait ScPatternsBase extends ScalaPsiElementBase { this: ScPatterns =>
  def patterns: Seq[ScPattern]
}