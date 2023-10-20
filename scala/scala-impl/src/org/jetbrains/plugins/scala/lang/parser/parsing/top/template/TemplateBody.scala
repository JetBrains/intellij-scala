package org.jetbrains.plugins.scala.lang.parser.parsing.top
package template

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.base.End
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.SelfType
import org.jetbrains.plugins.scala.lang.parser.util.InScala3
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils.parseRuleInBlockOrIndentationRegion
import org.jetbrains.plugins.scala.lang.parser.{BlockIndentation, ErrMsg, ScalaElementType}

sealed abstract class Body(indentationCanStartWithoutColon: Boolean = false) extends ParsingRule {

  import ScalaTokenTypes._

  protected def statementRule: Stat

  override final def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()
    builder.enableNewlines()

    var canAcceptEnd = false
    var hasBraces = false
    val (blockIndentation, baseIndentation) = builder.getTokenType match {
      case `tLBRACE` =>
        hasBraces = true
        builder.enterBracedRegion()
        builder.advanceLexer() // Ate {
        BlockIndentation.create -> None
      case tok if builder.isScala3IndentationBasedSyntaxEnabled =>
        tok match {
          case InScala3(ScalaTokenTypes.tCOLON) =>
            builder.advanceLexer() // Ate :
            canAcceptEnd = true
          case InScala3(_) if indentationCanStartWithoutColon =>
            canAcceptEnd = true
          case _ =>
            marker.drop()
            builder.restoreNewlinesState()
            return true
        }

        val currentIndent = builder.currentIndentationWidth
        val prevIndent = builder.findPreviousIndent
        prevIndent match {
          case None if builder.getTokenType != null =>
            // if something else comes after the colon in the same line
            // we have to rollback because it might be a typing like
            //
            //   call(new Blob: Ty)
            //
            // except if we are at eof. Then everything is fine
            marker.rollbackTo()
            builder.restoreNewlinesState()
            return false
          case indentO@Some(indent) if indent > currentIndent =>
            BlockIndentation.noBlock -> indentO
          case _ =>
            val endsWithEndMarker = if (canAcceptEnd)
              End(builder.currentIndentationWidth)
            else
              false
            if (!endsWithEndMarker) {
              builder.error(ScalaBundle.message("indented.definitions.expected"))
            }
            marker.done(ScalaElementType.TEMPLATE_BODY)
            builder.restoreNewlinesState()
            return true
        }
      case _ =>
        marker.drop()
        builder.restoreNewlinesState()
        return false
    }

    builder.maybeWithIndentationWidth(baseIndentation) {
      builder.insideBracedRegionIf(hasBraces) {
        SelfType()
        parseRuleInBlockOrIndentationRegion(blockIndentation, baseIndentation, ErrMsg("def.dcl.expected")) {
          statementRule()
        }
      }
    }

    if (hasBraces) builder.exitBracedRegion()
    blockIndentation.drop()
    builder.restoreNewlinesState()
    if (canAcceptEnd)
      End(builder.currentIndentationWidth)
    marker.done(ScalaElementType.TEMPLATE_BODY)

    true
  }
}

/**
 * [[TemplateBody]] ::= [cnl] '{' [ [[SelfType]] ] [[TemplateStat]] { semi [[TemplateStat]] } '}'
 */
object TemplateBody extends Body {
  override protected def statementRule: TemplateStat.type = TemplateStat
}

object GivenTemplateBody extends Body(indentationCanStartWithoutColon = true) {
  override protected def statementRule: TemplateStat.type = TemplateStat
}

/**
 * {{{
 *   EnumBody  ::=  :<<< [SelfType] EnumStat { semi EnumStat } >>>
 * }}}
 */
object EnumBody extends Body {
  override protected def statementRule: EnumStat.type = EnumStat
}
