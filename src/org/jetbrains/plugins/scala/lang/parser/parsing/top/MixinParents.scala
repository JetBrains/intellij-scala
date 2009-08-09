package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import types.AnnotType

/**
* @author Alexander Podkhalyuzin
* Date: 06.02.2008
*/

/*
 * MixinParents ::= AnnotType {'with' AnnotType}
 */

object MixinParents {
  def parse(builder: PsiBuilder): Boolean = {
    val mixinMarker = builder.mark
    //Look for parent
    if (!AnnotType.parse(builder)) {
      builder error ScalaBundle.message("wrong.simple.type")
      mixinMarker.done(ScalaElementTypes.TRAIT_PARENTS)
      return false
    }
    //Look for mixin
    while (builder.getTokenType == ScalaTokenTypes.kWITH) {
      builder.advanceLexer //Ate with
      if (!AnnotType.parse(builder)) {
        builder error ScalaBundle.message("wrong.simple.type")
        mixinMarker.done(ScalaElementTypes.TRAIT_PARENTS)
        return false
      }
    }
    mixinMarker.done(ScalaElementTypes.TRAIT_PARENTS)
    return true
  }
}