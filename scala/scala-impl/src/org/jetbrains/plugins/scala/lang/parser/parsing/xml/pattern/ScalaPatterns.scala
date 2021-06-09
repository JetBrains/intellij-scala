package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package xml.pattern

import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenTypes, ScalaTokenTypesEx}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns._
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

/**
* @author Alexander Podkhalyuzin
* Date: 21.04.2008
*/

object ScalaPatterns {

  def parse(builder: ScalaPsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_START =>
        builder.advanceLexer()
        builder.enableNewlines()
      case _ => return false
    }
    if (!parseXml(builder)) builder error ErrMsg("xml.scala.patterns.exected")
    builder.getTokenType match {
      case ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_END =>
        builder.advanceLexer()
      case _ => builder error ErrMsg("xml.scala.injection.end.expected")
    }
    builder.restoreNewlinesState()
    true
  }

  private def parseXml(builder: ScalaPsiBuilder): Boolean = {
    val args = builder.mark
    def parseSeqWildcard(withComma: Boolean): Boolean = {
      if (if (withComma)
        builder.lookAhead(ScalaTokenTypes.tCOMMA, ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER)
      else builder.lookAhead(ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER)) {
        if (withComma) builder.advanceLexer()
        val wild = builder.mark
        builder.getTokenType
        builder.advanceLexer()
        if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER && "*".equals(builder.getTokenText)) {
          builder.advanceLexer()
          wild.done(ScalaElementType.SEQ_WILDCARD_PATTERN)
          true
        } else {
          wild.rollbackTo()
          false
        }
      } else {
        false
      }
    }

    def parseSeqWildcardBinding(withComma: Boolean): Boolean = {
      if (if (withComma) builder.lookAhead(ScalaTokenTypes.tCOMMA, ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tAT,
        ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER)
      else builder.lookAhead(ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tAT,
        ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER)) {
        ParserUtils.parseVarIdWithWildcardBinding(builder, withComma)
      } else false
    }

    if (!parseSeqWildcard(false) && !parseSeqWildcardBinding(false) && Pattern.parse(builder)) {
      while (builder.getTokenType == ScalaTokenTypes.tCOMMA && !parseSeqWildcard(true) && !parseSeqWildcardBinding(true)) {
        builder.advanceLexer() // eat comma
        Pattern.parse(builder)
      }
    }
    args.done(ScalaElementType.PATTERNS)
    true
  }

}