package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.params.{FunTypeParamClause, ParamClauses}

//TODO: rewrite this
object FunSig extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER) {
      builder.advanceLexer()
      FunTypeParamClause()
      ParamClauses()
      true
    } else {
      builder error ScalaBundle.message("identifier.expected")
      false
    }

  }
}