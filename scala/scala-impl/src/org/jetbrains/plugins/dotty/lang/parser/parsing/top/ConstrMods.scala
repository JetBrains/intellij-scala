package org.jetbrains.plugins.dotty.lang.parser.parsing.top

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.AccessModifier
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
  * @author adkozlov
  */
object ConstrMods {

  def parseModifier(builder: ScalaPsiBuilder): Boolean = builder.getTokenType match {
    case ScalaTokenTypes.kTHIS =>
      builder.advanceLexer() // Ate this
      true
    case _ => AccessModifier.parse(builder)
  }
}
