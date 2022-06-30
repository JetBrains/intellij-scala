package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.TypePattern

/**
 * TypeCaseClause ::= ‘case’ TypePattern ‘=>’ Type [nl]
 */
object TypeCaseClause extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()
    builder.getTokenType match {
      case ScalaTokenTypes.kCASE =>
        builder.advanceLexer()
        builder.disableNewlines()
      case _ =>
        marker.drop()
        return false
    }

    if (!TypePattern()) builder.error(ScalaBundle.message("wrong.type"))

    builder.getTokenType match {
      case ScalaTokenTypes.tFUNTYPE =>
        builder.advanceLexer()
        builder.restoreNewlinesState()
      case _ =>
        builder.restoreNewlinesState()
        builder.error(ScalaBundle.message("fun.sign.expected"))
        marker.done(ScalaElementType.TYPE_CASE_CLAUSE)
        return true
    }

    if (!Type()) builder.error(ScalaBundle.message("wrong.type"))
    marker.done(ScalaElementType.TYPE_CASE_CLAUSE)
    true
  }
}
