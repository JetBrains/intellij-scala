package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.AnnotType

/**
* @author Alexander Podkhalyuzin
* Date: 06.02.2008
*/

/*
 * MixinParents ::= AnnotType {'with' AnnotType}
 */
object MixinParents extends MixinParents {
  override protected val annotType = AnnotType
}

trait MixinParents {
  protected val annotType: AnnotType

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val mixinMarker = builder.mark
    //Look for parent
    if (!annotType.parse(builder, isPattern = false)) {
      builder error ScalaBundle.message("wrong.simple.type")
      mixinMarker.done(ScalaElementTypes.TRAIT_PARENTS)
      return false
    }
    //Look for mixin
    while (builder.getTokenType == ScalaTokenTypes.kWITH) {
      builder.advanceLexer() //Ate with
      if (!annotType.parse(builder, isPattern = false)) {
        builder error ScalaBundle.message("wrong.simple.type")
        mixinMarker.done(ScalaElementTypes.TRAIT_PARENTS)
        return false
      }
    }
    mixinMarker.done(ScalaElementTypes.TRAIT_PARENTS)
    true
  }
}