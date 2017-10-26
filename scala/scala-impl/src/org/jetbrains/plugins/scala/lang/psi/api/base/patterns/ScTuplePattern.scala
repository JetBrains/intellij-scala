package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.types.api.TupleType
import org.jetbrains.plugins.scala.lang.psi.types.result._

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

trait ScTuplePattern extends ScPattern {
  def patternList: Option[ScPatterns] = findChild(classOf[ScPatterns])

  override def `type`(): TypeResult = this.flatMap(patternList) { list =>
    val types = list.patterns.map(_.`type`().getOrAny)
    Right(TupleType(types))
  }
}