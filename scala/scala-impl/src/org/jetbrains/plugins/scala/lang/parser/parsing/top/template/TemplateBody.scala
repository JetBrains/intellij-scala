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
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementType}

sealed abstract class Body(indentationCanStartWithoutColon: Boolean = false) extends ParsingRule {

  import ScalaTokenTypes._

  protected def statementRule: Stat
  protected def generateIndentedDefinitionsExpectedError: Boolean = true

  override final def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()
    builder.enableNewlines()

    val region = builder.getTokenType match {
      case `tLBRACE` =>
        builder.advanceLexer() // Ate {
        builder.newBracedIndentationRegionHere
      case tok if builder.isScala3IndentationBasedSyntaxEnabled =>
        tok match {
          case InScala3(ScalaTokenTypes.tCOLON) =>
            builder.advanceLexer() // Ate :
          case InScala3(_) if indentationCanStartWithoutColon =>
          case _ =>
            marker.drop()
            builder.restoreNewlinesState()
            return true
        }

        val prevIndent = builder.findPrecedingIndentation
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
          case Some(indent) if builder.isIndent(indent) =>
            builder.newBracelessIndentationRegionHere.get
          case _ =>
            val endsWithEndMarker = End()
            if (!endsWithEndMarker && generateIndentedDefinitionsExpectedError) {
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

    builder.withIndentationRegion(region) {
      SelfType()
      parseRuleInBlockOrIndentationRegion(region, ErrMsg("def.dcl.expected")) {
        statementRule()
      }
    }

    builder.restoreNewlinesState()
    End()
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
  //scala compiler does not generate the error for given definitions block
  override protected def generateIndentedDefinitionsExpectedError: Boolean = false
}

/**
 * {{{
 *   EnumBody  ::=  :<<< [SelfType] EnumStat { semi EnumStat } >>>
 * }}}
 */
object EnumBody extends Body {
  override protected def statementRule: EnumStat.type = EnumStat
}
