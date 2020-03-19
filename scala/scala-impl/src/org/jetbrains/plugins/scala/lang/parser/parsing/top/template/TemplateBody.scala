package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top
package template

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
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

    val indentation = builder.getTokenType match {
      case `tLBRACE` =>
        builder.advanceLexer() // Ate {
        None
      case _ if builder.isScala3 =>
        val hadColon = builder.getTokenType == ScalaTokenTypes.tCOLON
        if (hadColon) {
          builder.advanceLexer() // Ate :
        }

        builder.findPreviousIndent match {
          case indentO@Some(indent) if indent > builder.currentIndentationWidth =>
            indentO
          case indentO =>
            if (hadColon && indentO.isEmpty) {
              builder error ScalaBundle.message("expected.indented.template.body")
            }
            marker.drop()
            builder.restoreNewlinesState()
            return true
        }
      case _ =>
        marker.drop()
        builder.restoreNewlinesState()
        return true
    }

    def nowrap(body: => Unit): Unit = body
    indentation.fold(nowrap _)(builder.withIndentationWidth) {
      SelfType.parse(builder)
      parseStatements(indentation)
    }

    builder.restoreNewlinesState()
    marker.done(ScalaElementType.TEMPLATE_BODY)

    true
  }


  /**
   * [[Stat]] { semi [[Stat]] }
   */
  @annotation.tailrec
  private def parseStatements(baseIndentation: Option[IndentationWidth])(implicit builder: ScalaPsiBuilder): Unit = {
    skipSemicolon()
    builder.getTokenType match {
      case null =>
        builder.error(ScalaBundle.message("rbrace.expected"))
        return
      case `tRBRACE` =>
        if (baseIndentation.isEmpty)
          builder.advanceLexer() // Ate }
        return
      case _ if isOutdent(baseIndentation) =>
        return
      case _ if statementRule() =>
        builder.getTokenType match {
          case `tRBRACE` =>
            if (baseIndentation.isEmpty)
              builder.advanceLexer() // Ate }
            return
          case _ if isOutdent(baseIndentation) =>
            return
          case `tSEMICOLON` =>
          case _ if builder.newlineBeforeCurrentToken =>
          case _ =>
            builder.error(ScalaBundle.message("semi.expected"))
            builder.advanceLexer() // Ate something
        }
      case _ =>
        builder.error(ScalaBundle.message("def.dcl.expected"))
        builder.advanceLexer() // Ate something
    }
    parseStatements(baseIndentation)
  }

  private def isOutdent(baseIndentation: Option[IndentationWidth])(implicit builder: ScalaPsiBuilder): Boolean =
    baseIndentation.exists(cur => builder.findPreviousIndent.exists(_ < cur) || builder.eof())

  private def skipSemicolon()(implicit builder: ScalaPsiBuilder): Unit =
    while (builder.getTokenType == tSEMICOLON) {
      builder.advanceLexer()
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