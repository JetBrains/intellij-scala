package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, TupleType}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

trait ScTuplePattern extends ScPattern {
  def patternList: Option[ScPatterns] = findChild(classOf[ScPatterns])

  override def `type`(): TypeResult[ScType] = wrap(patternList) flatMap { l =>
    collectFailures(l.patterns.map(_.`type`()), Any)(TupleType(_))
  }
}