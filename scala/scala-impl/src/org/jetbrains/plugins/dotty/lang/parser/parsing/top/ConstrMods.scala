package org.jetbrains.plugins.dotty.lang.parser.parsing.top

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
  * @author adkozlov
  */
object ConstrMods extends org.jetbrains.plugins.scala.lang.parser.parsing.top.ConstrMods {
  override protected def parseModifier(builder: ScalaPsiBuilder) = builder.getTokenType match {
    case ScalaTokenTypes.kTHIS =>
      builder.advanceLexer() // Ate this
      true
    case _ => super.parseModifier(builder)
  }
}
