package org.jetbrains.plugins.scala
package lang.psi.api

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScInterpolationPattern
import org.jetbrains.plugins.scala.lang.psi.types

/**
 * @author kfeodorov 
 * @since 13.03.14.
 */
object InterpreterUtil {
  def interpret(p: ScInterpolationPattern): Seq[ScType] = Seq(types.Any)
}
