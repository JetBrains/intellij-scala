package org.jetbrains.plugins.scala
package lang.psi.api.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolated
import com.intellij.psi.PsiLiteral
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
 * @author kfeodorov
 * @since 01.03.14.
 */
trait ScInterpolationPattern extends ScConstructorPattern with ScInterpolated {
  //TODO KOS fix it
  override def getType(ctx: TypingContext): TypeResult[ScType] = Failure("Cannot type pattern", Some(this))
}
