package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top
package template

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.SelfType
import org.jetbrains.plugins.scala.lang.parser.util.InScala3
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils.parseRuleInBlockOrIndentationRegion

/**
 * @author Alexander Podkhalyuzin
 *         Date: 08.02.2008
 */
sealed abstract class Body extends ParsingRule {

  import lexer.ScalaTokenTypes._

  protected def statementRule: Stat

  override final def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()
    builder.enableNewlines()

    val (blockIndentation, baseIndentation) = builder.getTokenType match {
      case `tLBRACE` =>
        builder.advanceLexer() // Ate {
        BlockIndentation.create -> None
      case InScala3(ScalaTokenTypes.tCOLON) =>
        builder.advanceLexer() // Ate :

        val currentIndent = builder.currentIndentationWidth
        builder.findPreviousIndent match {
          case None =>
            // if something else comes after the colon in the same line
            // we have to rollback because it might be a typing like
            //
            //   call(new Blob: Ty)
            marker.rollbackTo()
            builder.restoreNewlinesState()
            return false
          case indentO@Some(indent) if indent > currentIndent =>
            BlockIndentation.noBlock -> indentO
          case _ =>
            marker.done(ScalaElementType.TEMPLATE_BODY)
            builder.restoreNewlinesState()
            return true
        }
      case _ =>
        marker.drop()
        builder.restoreNewlinesState()
        return true
    }

    builder.maybeWithIndentationWidth(baseIndentation) {
      SelfType.parse(builder)
      parseRuleInBlockOrIndentationRegion(blockIndentation, baseIndentation, ErrMsg("def.dcl.expected")) {
        statementRule()
      }
    }

    blockIndentation.drop()
    builder.restoreNewlinesState()
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

/**
 * [[EnumBody]] ::= [cnl] '{' [ [[SelfType]] ] [[EnumStat]] { semi [[EnumStat]] } '}'
 */
object EnumBody extends Body {
  override protected def statementRule: EnumStat.type = EnumStat
}