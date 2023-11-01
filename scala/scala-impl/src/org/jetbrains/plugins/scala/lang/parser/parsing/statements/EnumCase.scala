package org.jetbrains.plugins.scala.lang.parser.parsing.statements

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.ScalaTokenBinders
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

  import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
  import org.jetbrains.plugins.scala.lang.parser.ScalaElementType._

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()
    marker.setCustomEdgeTokenBinders(ScalaTokenBinders.PRECEDING_COMMENTS_TOKEN, null)

    Annotations.parseAndBindToLeft()

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
              singleCaseMarker.done(EnumSingletonCase)
              builder.disableNewlines()
              while (builder.getTokenType == tCOMMA) {
                builder.advanceLexer() // consume ,
                if (!parseSingletonCase()) builder.error(ScalaBundle.message("identifier.expected"))
              }
              builder.restoreNewlinesState()
            } else {
              val previousOffset = builder.getCurrentOffset
              EnumCaseConstr()
              val hasParameters = builder.getCurrentOffset == previousOffset
              parseParents()
              singleCaseMarker.done(if (hasParameters) EnumSingletonCase else EnumClassCase)
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
        marker.done(EnumSingletonCase)
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
