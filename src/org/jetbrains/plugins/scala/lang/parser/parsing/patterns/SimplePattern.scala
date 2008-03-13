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

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 29.02.2008
* Time: 18:10:24
* To change this template use File | Settings | File Templates.
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

object SimplePattern {
  def parse(builder: PsiBuilder): Boolean = {
    val simplePatternMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tUNDER => {
        builder.advanceLexer //Ate _
        simplePatternMarker.done(ScalaElementTypes.WILD_PATTERN)
        return true
      }
      case ScalaTokenTypes.tLPARENTHESIS => {
        builder.advanceLexer //Ate (
        builder.getTokenType match {
          case ScalaTokenTypes.tRPARENTHESIS => {
            builder.advanceLexer //Ate )
            simplePatternMarker.done(ScalaElementTypes.SIMPLE_PATTERN)
            return true
          }
          case _ => {}
        }
        if (Patterns parse builder) {
          builder.getTokenType match {
            case ScalaTokenTypes.tRPARENTHESIS => {
              builder.advanceLexer //Ate )
              simplePatternMarker.done(ScalaElementTypes.SIMPLE_PATTERN)
              return true
            }
            case _ => {
              builder error ScalaBundle.message("rparenthesis.expected", new Array[Object](0))
              simplePatternMarker.done(ScalaElementTypes.SIMPLE_PATTERN)
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
              builder error ScalaBundle.message("rparenthesis.expected", new Array[Object](0))
            }
          }
          simplePatternMarker.done(ScalaElementTypes.PATTERN_IN_PARENTHESIS)
          return true
        }
      }
      case _ => {}
    }
    if (Literal parse builder) {
      simplePatternMarker.done(ScalaElementTypes.LITERAL_PATTERN)
      return true
    }
    if (StableId parse builder) {
      builder.getTokenType match {
        case ScalaTokenTypes.tLPARENTHESIS => {
          builder.advanceLexer //Ate (
          if (!Patterns.parse(builder))
          if (!Pattern.parse(builder)) {
            builder error ScalaBundle.message("wrong.pattern", new Array[Object](0))
          }
          builder.getTokenType match {
            case ScalaTokenTypes.tRPARENTHESIS => {
              builder.advanceLexer //Ate )
            }
            case _ => {
              builder error ScalaBundle.message("rparenthesis.expected", new Array[Object](0))
            }
          }
          simplePatternMarker.done(ScalaElementTypes.SIMPLE_PATTERN1)
          return true
        }
        case _ => {
          simplePatternMarker.done(ScalaElementTypes.REFERENCE_PATTERN)
          return true
        }
      }
    }
    simplePatternMarker.rollbackTo
    return false
  }
}