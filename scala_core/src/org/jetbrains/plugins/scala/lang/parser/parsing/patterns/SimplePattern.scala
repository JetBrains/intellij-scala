package org.jetbrains.plugins.scala.lang.parser.parsing.patterns

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template._
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._
import org.jetbrains.plugins.scala.lang.parser.bnf._
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator
import org.jetbrains.plugins.scala.lang.parser.parsing.xml.pattern.XmlPattern

/** 
* @author Alexander Podkhalyuzin
* Date: 29.02.2008
*/

/*
 * SimplePattern ::= '_'
 *                 | varid
 *                 | Literal
 *                 | StableId
 *                 | StableId '(' [Patterns [',']] ')'
 *                 | StableId '(' [Patterns ','] '-' '*'')'
 *                 |'(' [Patterns [',']] ')'
 *                 | XmlPattern //Todo: xmlPattern
 */

object SimplePattern extends ParserNode {
  def parse(builder: PsiBuilder): Boolean = {
    val simplePatternMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tUNDER => {
        builder.advanceLexer //Ate _
        builder getTokenText match {
          case "*" => {
            simplePatternMarker.rollbackTo
            return false
          }
          case _ => {}
        }
        simplePatternMarker.done (ScalaElementTypes.WILDCARD_PATTERN)
        return true
      }
      case ScalaTokenTypes.tLPARENTHESIS => {
        builder.advanceLexer //Ate (
        builder.getTokenType match {
          case ScalaTokenTypes.tRPARENTHESIS => {
            builder.advanceLexer //Ate )
            simplePatternMarker.done (ScalaElementTypes.TUPLE_PATTERN)
            return true
          }
          case _ => {}
        }
        if (Patterns parse builder) {
          builder.getTokenType match {
            case ScalaTokenTypes.tRPARENTHESIS => {
              builder.advanceLexer //Ate )
              simplePatternMarker.done (ScalaElementTypes.TUPLE_PATTERN)
              return true
            }
            case _ => {
              builder error ScalaBundle.message ("rparenthesis.expected", new Array [Object](0))
              simplePatternMarker.done (ScalaElementTypes.TUPLE_PATTERN)
              return true
            }
          }
        }
        if (Pattern parse builder) {
          builder.getTokenType match {
            case ScalaTokenTypes.tRPARENTHESIS => {
              builder.advanceLexer //Ate )
            }
            case _ => {
              builder error ScalaBundle.message ("rparenthesis.expected", new Array [Object](0))
            }
          }
          simplePatternMarker.done (ScalaElementTypes.PATTERN_IN_PARENTHESIS)
          return true
        }
      }
      case _ => {}
    }
    if (Literal parse builder) {
      simplePatternMarker.done (ScalaElementTypes.LITERAL_PATTERN)
      return true
    }
    if (XmlPattern.parse(builder)) {
      simplePatternMarker.drop
      return true
    }
    if (StableId parse (builder, ScalaElementTypes.REFERENCE_PATTERN)) {
      builder.getTokenType match {
        case ScalaTokenTypes.tLPARENTHESIS => {
          val args = builder.mark
          builder.advanceLexer //Ate (

          def parseSeqWildcard(withComma: Boolean) = {
            if (if (withComma)
                  lookAhead (builder, ScalaTokenTypes.tCOMMA, ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER)
                  else lookAhead (builder, ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER)) {
              val wild = builder.mark
              if (withComma) builder.advanceLexer
              builder.getTokenType()
              builder.advanceLexer
              if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER && "*".equals (builder.getTokenText)) {
                builder.advanceLexer
                wild.done (ScalaElementTypes.SEQ_WILDCARD)
                true
              } else {
                wild.rollbackTo
                false
              }
            } else {
              false
            }
          }

          if (parseSeqWildcard(false) || Pattern.parse (builder)) {
            while (builder.getTokenType == ScalaTokenTypes.tCOMMA && !parseSeqWildcard(true)) {
              builder.advanceLexer // eat comma
              Pattern.parse (builder)
            }
          }
          builder.getTokenType match {
            case ScalaTokenTypes.tRPARENTHESIS => {
              builder.advanceLexer //Ate )
            }
            case _ => {
              builder error ErrMsg ("rparenthesis.expected")
            }
          }
          args.done (ScalaElementTypes.PATTERN_ARGS)
          simplePatternMarker.done (ScalaElementTypes.CONSTRUCTOR_PATTERN)
          return true
        }
        case _ => {
          simplePatternMarker.drop
          return true
        }
      }
    }
    simplePatternMarker.rollbackTo
    return false
  }
}