package org.jetbrains.plugins.scala.lang.parser.parsing.patterns

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import util.ParserUtils

/**
* @author Alexander Podkhalyuzin
* Date: 29.02.2008
*/

object Patterns {
  def parse(builder: PsiBuilder): Boolean = parse(builder,false)
  def parse(builder: PsiBuilder, underParams: Boolean): Boolean = {
    val patternsMarker = builder.mark
    if (!Pattern.parse(builder)) {
      builder.getTokenType match {
        case ScalaTokenTypes.tUNDER => {
          builder.advanceLexer()
          builder.getTokenText match {
            case "*" => {
              builder.advanceLexer
              patternsMarker.done(ScalaElementTypes.SEQ_WILDCARD)
              return true
            }
            case _ =>
          }
        }
        case _=>
      }
      patternsMarker.rollbackTo
      return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tCOMMA => {
        builder.advanceLexer //Ate ,
        var end = false
        while ((!end || !underParams) && Pattern.parse(builder)) {
          builder.getTokenType match {
            case ScalaTokenTypes.tCOMMA => {
              builder.advanceLexer //Ate ,
              if (ParserUtils.eatSeqWildcardNext(builder) && underParams) end = true
            }
            case _ => {
              patternsMarker.done(ScalaElementTypes.PATTERNS)
              return true
            }
          }
        }
        if (underParams) {
          ParserUtils.eatSeqWildcardNext(builder)
        }
        patternsMarker.done(ScalaElementTypes.PATTERNS)
        return true
      }
      case _ => {
        patternsMarker.rollbackTo
        return false
      }
    }
  }
}