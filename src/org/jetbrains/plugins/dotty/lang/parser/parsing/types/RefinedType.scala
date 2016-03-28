package org.jetbrains.plugins.dotty.lang.parser.parsing.types

import org.jetbrains.plugins.dotty.lang.parser.DottyElementTypes._
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
  * @author adkozlov
  */

/*
 * RefinedType ::= WithType {[nl] Refinement}
 */
object RefinedType extends org.jetbrains.plugins.scala.lang.parser.parsing.types.Type {
  override protected val infixType = InfixType

  override def parse(builder: ScalaPsiBuilder, star: Boolean, isPattern: Boolean): Boolean = {
    val marker = builder.mark()
    if (!WithType.parse(builder, star, isPattern)) {
      marker.drop()
      return false
    }

    var isDone = false
    while (Refinement.parse(builder)) {
      isDone = true
    }

    if (isDone) {
      marker.done(REFINED_TYPE)
    } else {
      marker.drop()
    }
    true
  }
}
