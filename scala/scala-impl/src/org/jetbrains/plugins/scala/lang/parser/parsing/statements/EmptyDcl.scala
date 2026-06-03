package org.jetbrains.plugins.scala.lang.parser.parsing.statements

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotation

object EmptyDcl {

  def apply(isMod: Boolean = true)(implicit builder: ScalaPsiBuilder): Boolean = {
    val dclMarker = builder.mark()
    if (isMod) {
      while (Annotation()) {}
      while (Modifier()) {}
    }
    builder.getTokenType match {
      case ScalaTokenTypes.kDEF | ScalaTokenTypes.kVAL | ScalaTokenTypes.kVAR |
           ScalaTokenTypes.kTYPE =>
        builder.advanceLexer()
        builder.error(ScalaBundle.message("identifier.expected"))
        dclMarker.drop()
        true
      case _ =>
        dclMarker.rollbackTo()
        false
    }
  }
}