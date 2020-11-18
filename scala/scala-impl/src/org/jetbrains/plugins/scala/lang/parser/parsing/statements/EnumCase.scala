package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import org.jetbrains.plugins.scala.lang.parser.parsing.base.{Ids, Modifier}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotations
import org.jetbrains.plugins.scala.lang.parser.parsing.params.EnumCaseConstr
import org.jetbrains.plugins.scala.lang.parser.parsing.top.ClassParents

/**
 * {{{
 *   EnumCase ::= 'case' ( id ClassConstr [ 'extends' ConstrApps ] | Ids )
 * }}}
 */
object EnumCase extends ParsingRule {

  import ScalaElementType.{EnumCase => SingleCase, _}
  import lexer.ScalaTokenTypes._

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()

    Annotations()

    val modifierMarker = builder.mark()
    while (Modifier.parse(builder)) {}
    modifierMarker.done(MODIFIERS)

    builder.getTokenType match {
      case `kCASE` =>
        builder.advanceLexer()

        builder.getTokenType match {
          case `tIDENTIFIER` =>
            val singleCaseMarker = builder.mark()
            builder.advanceLexer()
            val parseMultipleSingletonCases = builder.getTokenType == tCOMMA

            if (parseMultipleSingletonCases) {
              builder.mark().done(ScalaElementType.EXTENDS_BLOCK)
              singleCaseMarker.done(SingleCase)
              builder.disableNewlines()
              while (builder.getTokenType == tCOMMA) {
                builder.advanceLexer() // consume ,
                if (!parseSingletonCase()) builder.error(ScalaBundle.message("identifier.expected"))
              }
              builder.restoreNewlinesState()
            } else {
              EnumCaseConstr()
              parseParents()
              singleCaseMarker.done(SingleCase)
            }

            marker.done(ScalaElementType.EnumCases)
            true
          case _ =>
            marker.rollbackTo()
            false
        }
      case _ =>
        marker.rollbackTo()
        false
    }
  }

  private def parseSingletonCase()(implicit builder: ScalaPsiBuilder): Boolean =
    builder.getTokenType match {
      case `tIDENTIFIER` =>
        val marker = builder.mark()
        builder.advanceLexer() // Ate identifier
        builder.mark().done(ScalaElementType.EXTENDS_BLOCK)
        marker.done(SingleCase)
        true
      case _ =>
        false
    }

  private def parseParents()(implicit builder: ScalaPsiBuilder): Unit = {
    val marker = builder.mark()

    builder.getTokenType match {
      case `kEXTENDS` =>
        builder.advanceLexer()
        ClassParents()
      case _ =>
    }

    marker.done(ScalaElementType.EXTENDS_BLOCK)
  }
}
