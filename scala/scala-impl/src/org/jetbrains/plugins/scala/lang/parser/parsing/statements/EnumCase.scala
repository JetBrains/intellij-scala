package org.jetbrains.plugins.scala.lang.parser.parsing.statements

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotations
import org.jetbrains.plugins.scala.lang.parser.parsing.params.EnumCaseConstr
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TypeDefinitionParents

/**
 * {{{
 *   EnumCase ::= 'case' ( id ClassConstr [ 'extends' ConstrApps ] | Ids )
 * }}}
 */
object EnumCase extends ParsingRule {

  import org.jetbrains.plugins.scala.lang.parser.ScalaElementType.{EnumCase => SingleCase, _}
  import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()

    Annotations()

    val modifierMarker = builder.mark()
    while (Modifier()) {}
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
              builder.mark().done(EXTENDS_BLOCK)
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

            marker.done(EnumCases)
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
        builder.mark().done(EXTENDS_BLOCK)
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
        TypeDefinitionParents()
      case _ =>
    }

    marker.done(EXTENDS_BLOCK)
  }
}
