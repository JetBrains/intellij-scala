package org.jetbrains.plugins.scala
package lang.psi.api.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolated
import org.jetbrains.plugins.scala.lang.psi.types.result.{TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
 * @author kfeodorov
 * @since 01.03.14.
 */
trait ScInterpolationPattern extends ScConstructorPattern with ScInterpolated {
  override def getType(ctx: TypingContext): TypeResult[ScType] = super[ScConstructorPattern].getType(ctx)
}
