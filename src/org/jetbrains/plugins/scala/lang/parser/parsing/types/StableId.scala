package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.InfixTemplate
import org.jetbrains.plugins.scala.ScalaBundle

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 15.02.2008
* Time: 15:47:11
* To change this template use File | Settings | File Templates.
*/

/*
 * StableId ::= id
 *            | Path '.' id
 *            | [id '.'] 'super' [ClassQualifier] '.' id
 */

object StableId {
  def parse(builder: PsiBuilder): Boolean = {
    var stableMarker = builder.mark
    def parseQualId(qualMarker: PsiBuilder.Marker): Boolean = {
      //parsing first identifier
      builder.getTokenType match {
        case ScalaTokenTypes.tIDENTIFIER => {
          builder.advanceLexer//Ate identifier
          //Look for dot
          builder.getTokenType match {
            case ScalaTokenTypes.tDOT => {
              val newMarker = qualMarker.precede
              val qual2Marker = qualMarker.precede
              qualMarker.done(ScalaElementTypes.STABLE_ID)
              qual2Marker.done(ScalaElementTypes.PATH)
              builder.advanceLexer//Ate dot
              //recursively parse qualified identifier
              parseQualId(newMarker)
              return true
            }
            case _ => {
              //It's OK, let's close marker
              qualMarker.done(ScalaElementTypes.STABLE_ID)
              return true
            }
          }
        }
        case _ => {
          builder error ScalaBundle.message("identifier.expected", new Array[Object](0))
          qualMarker.done(ScalaElementTypes.STABLE_ID)
          return true
        }
      }
    }
    def parseOtherCases(stableMarker: PsiBuilder.Marker): Boolean = {
      builder.getTokenType match {
        //In this case we of course know - Path.id
        case ScalaTokenTypes.kTHIS => {
          Path parse builder
          builder.getTokenType match {
            case ScalaTokenTypes.tDOT => {
              builder.advanceLexer //Ate .
              return parseQualId(stableMarker)
            }
            case _ => {
              builder error ScalaBundle.message("identifier.expected", new Array[Object](0))
              stableMarker.done(ScalaElementTypes.STABLE_ID)
              return true
            }
          }
        }
        //In this case of course it's super[id].id
        case ScalaTokenTypes.kSUPER => {
          builder.advanceLexer //Ate super
          builder.getTokenType match {
            case ScalaTokenTypes.tDOT => {
              builder.advanceLexer //Ate .
              return parseQualId(stableMarker)
            }
            case ScalaTokenTypes.tLSQBRACKET => {
              builder.advanceLexer //Ate [
              builder.getTokenType match {
                case ScalaTokenTypes.tIDENTIFIER => {
                  builder.advanceLexer //Ate id
                }
                case _ => {
                  builder error ScalaBundle.message("identifier.expected", new Array[Object](0))
                }
              }
              builder.getTokenType match {
                case ScalaTokenTypes.tRSQBRACKET => {
                  builder.advanceLexer //Ate ]
                }
                case _ => {
                  builder error ScalaBundle.message("rsqbracket.expected", new Array[Object](0))
                }
              }
              builder.getTokenType match {
                case ScalaTokenTypes.tDOT => {
                  builder.advanceLexer //Ate .
                  return parseQualId(stableMarker)
                }
                case _ => {
                  builder error ScalaBundle.message("identifier.expected", new Array[Object](0))
                  stableMarker.done(ScalaElementTypes.STABLE_ID)
                  return true
                }
              }
            }
            case _ => {
              builder error ScalaBundle.message("identifier.expected", new Array[Object](0))
              stableMarker.done(ScalaElementTypes.STABLE_ID)
              return true
            }
          }
        }
        case _ => {
          stableMarker.rollbackTo
          return false
        }
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        builder.advanceLexer //Ate identifier
        builder.getTokenType match {
          case ScalaTokenTypes.tDOT => {
            builder.advanceLexer //Ate .
            val newMarker = stableMarker.precede
            if (parseOtherCases(stableMarker)) {
              newMarker.drop
              return true
            }
            else {
              newMarker.rollbackTo
              stableMarker = builder.mark
              return parseQualId(stableMarker)
            }
          }
          case _ => {
            stableMarker.done(ScalaElementTypes.STABLE_ID)
            return true
          }
        }
      }
      case _ => {
        return parseOtherCases(stableMarker)
      }
    }
  }
}