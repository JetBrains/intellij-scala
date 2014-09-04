package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.types.{Any, ScTupleType}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

trait ScTuplePattern extends ScPattern {
  def patternList = findChild(classOf[ScPatterns])

  override def getType(ctx: TypingContext) = wrap(patternList) flatMap {l =>
    collectFailures(l.patterns.map(_.getType(ctx)), Any)(ScTupleType(_)(getProject, getResolveScope))
  }
}