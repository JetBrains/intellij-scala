package org.jetbrains.plugins.scala.lang.psi.api.base
package patterns

import org.jetbrains.plugins.scala.lang.psi.types.api.TupleType
import org.jetbrains.plugins.scala.lang.psi.types.result._

trait ScTuplePattern extends ScPattern {
  def patternList: Option[ScPatterns] = findChild[ScPatterns]

  override def `type`(): TypeResult = this.flatMap(patternList) { list =>
    val types = list.patterns.map(_.`type`().getOrAny)
    Right(TupleType(types, context = this))
  }

  /**
   * In Scala 2, TuplePatterns on the right side of infix patterns become arg-pattern lists of that pattern.
   * Example: case a + (b, c) =>
   * Here +.unapply must return 3 elements to match.
   * (T, (T, T)) is not matched.
   */
  def infixPatternOfWhichThisIsTheArgPatternList: Option[ScInfixPattern]
}

object ScTuplePattern {
  def unapply(tp: ScTuplePattern): Option[ScPatterns] = tp.patternList
}