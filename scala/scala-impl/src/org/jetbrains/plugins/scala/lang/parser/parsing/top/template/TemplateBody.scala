package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top
package template

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.SelfType

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

    builder.getTokenType match {
      case `tLBRACE` =>
        builder.advanceLexer() // Ate {
      case _ =>
        builder.error(ScalaBundle.message("lbrace.expected"))
    }

    SelfType.parse(builder)
    parseStatements()

    builder.restoreNewlinesState()
    marker.done(ScalaElementType.TEMPLATE_BODY)

    true
  }


  /**
   * [[Stat]] { semi [[Stat]] }
   */
  @annotation.tailrec
  private def parseStatements()(implicit builder: ScalaPsiBuilder): Unit = builder.getTokenType match {
    case null =>
      builder.error(ScalaBundle.message("rbrace.expected"))
    case `tRBRACE` =>
      builder.advanceLexer() // Ate }
    case _ if statementRule() =>
      builder.getTokenType match {
        case `tRBRACE` =>
          builder.advanceLexer() // Ate }
        case `tSEMICOLON` =>
          while (builder.getTokenType == tSEMICOLON) {
            builder.advanceLexer()
          }
          parseStatements()
        case _ if builder.newlineBeforeCurrentToken =>
          parseStatements()
        case _ =>
          builder.error(ScalaBundle.message("semi.expected"))
          builder.advanceLexer() // Ate something
          parseStatements()
      }
    case _ =>
      builder.error(ScalaBundle.message("def.dcl.expected"))
      builder.advanceLexer() // Ate something
      parseStatements()
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