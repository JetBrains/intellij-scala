package org.jetbrains.plugins.scala.lang.parser.parsing.statements

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaParserBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.params.{FunTypeParamClause, ParamClauses}

//TODO: rewrite this
object FunSig extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER) {
      builder.advanceLexer()
      FunTypeParamClause()
      ParamClauses(allowInterleavingTypeParamClauses = builder.isScala3)
      true
    } else {
      builder.error(ScalaParserBundle.message("identifier.expected"))
      false
    }

  }
}
