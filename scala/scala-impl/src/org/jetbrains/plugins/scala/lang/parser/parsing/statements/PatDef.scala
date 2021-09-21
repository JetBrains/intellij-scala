package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.{Ascription, ExprInIndentationRegion}
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.Pattern2InForDef

/*
 * Scala 2.12
 * PatDef ::= Pattern2 {',' Pattern2} [':' Type] '=' Expr
 *
 * Scala 3
 * PatDef ::= ids [‘:’ Type] ‘=’ Expr
 *          | Pattern2 [‘:’ Type | Ascription] ‘=’ Expr
 */
object PatDef extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val patDefMarker = builder.mark()

    def parsePatterns(): Boolean = {
      if (!Pattern2InForDef()) {
        return false
      }
      while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
        builder.checkedAdvanceLexer()
        if (!Pattern2InForDef()) {
          // do some error recovery
          builder.getTokenType match {
            case ScalaTokenTypes.tCOMMA =>
              builder.error(ScalaBundle.message("expected.another.pattern"))
            case ScalaTokenTypes.tCOLON | ScalaTokenTypes.tASSIGN =>
              builder.error(ScalaBundle.message("expected.another.pattern"))
              return true
            case _ =>
              return false
          }
        }
      }
      true
    }

    def parseTypeOrAnnotationAscription(): Unit =
      if (builder.getTokenType == ScalaTokenTypes.tCOLON) {
        Ascription()
      }

    def parseAssignExpression(): Boolean = {
      if (builder.getTokenType == ScalaTokenTypes.tASSIGN) {
        builder.checkedAdvanceLexer()

        if (!ExprInIndentationRegion()) {
          builder.error(ErrMsg("expression.expected"))
        }

        patDefMarker.drop()
        true
      } else {
        patDefMarker.rollbackTo()
        false
      }
    }


    val patternsMarker = builder.mark()
    if (!parsePatterns()) {
      patternsMarker.rollbackTo()
      patDefMarker.drop()
      return false
    }
    patternsMarker.done(ScalaElementType.PATTERN_LIST)

    parseTypeOrAnnotationAscription()
    parseAssignExpression()
  }
}