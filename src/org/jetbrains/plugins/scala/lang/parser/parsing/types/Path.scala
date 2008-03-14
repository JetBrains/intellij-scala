package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.ScalaBundle

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 15.02.2008
* Time: 15:43:28
* To change this template use File | Settings | File Templates.
*/

/*
 * Path ::= StableId
 *        | [id '.'] 'this'
 */

object Path {
  def parse(builder: PsiBuilder,element: ScalaElementType): Boolean = parse(builder,false,element)
  def parse(builder: PsiBuilder,dot: Boolean,element: ScalaElementType): Boolean = {
    val pathMarker = builder.mark
    def parseQualId(qualMarker: PsiBuilder.Marker): Boolean = {
      //parsing first identifier
      builder.getTokenType match {
        case ScalaTokenTypes.tIDENTIFIER => {
          builder.advanceLexer//Ate identifier
          //Look for dot
          builder.getTokenType match {
            case ScalaTokenTypes.tDOT => {
              if (dot) {
                val dotMarker = builder.mark
                builder.advanceLexer //Ate .
                builder.getTokenType match {
                  case ScalaTokenTypes.tIDENTIFIER => {
                    dotMarker.rollbackTo
                  }
                  case _ => {
                    dotMarker.rollbackTo
                    qualMarker.done(element)
                    return true
                  }
                }
              }
              val newMarker = qualMarker.precede
              val qual2Marker = qualMarker.precede
              qualMarker.done(element)
              qual2Marker.drop
              builder.advanceLexer//Ate dot
              //recursively parse qualified identifier
              parseQualId(newMarker)
              return true
            }
            case _ => {
              //It's OK, let's close marker
              qualMarker.done(element)
              return true
            }
          }
        }
        case _ => {
          builder error ScalaBundle.message("identifier.expected", new Array[Object](0))
          qualMarker.done(element)
          return true
        }
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        builder.advanceLexer //Ate identifier
        builder.getTokenType match {
          case ScalaTokenTypes.tDOT => {
            builder.advanceLexer //Ate .
            builder.getTokenType match {
              case ScalaTokenTypes.kTHIS => {
                builder.advanceLexer //Ate this
                val newMarker = pathMarker.precede
                pathMarker.done(ScalaElementTypes.THIS_REFERENCE)
                builder.getTokenType match {
                  case ScalaTokenTypes.tDOT => {
                    builder.advanceLexer //Ate .
                    parseQualId(newMarker)
                  }
                  case _ => {
                    newMarker.drop
                  }
                }
                return true
              }
              case _ => {
                val newMarker = pathMarker.precede
                pathMarker.rollbackTo
                StableId parse (builder,element)
                newMarker.drop
                return true
              }
            }
          }
          case _ => {
            val newMarker = pathMarker.precede
            pathMarker.rollbackTo
            StableId parse (builder,dot,element)
            newMarker.drop
            return true
          }
        }
      }
      case ScalaTokenTypes.kTHIS => {
        builder.advanceLexer //Ate this
        val newMarker = pathMarker.precede
        pathMarker.done(ScalaElementTypes.THIS_REFERENCE)
        builder.getTokenType match {
          case ScalaTokenTypes.tDOT => {
            builder.advanceLexer //Ate .
            parseQualId(newMarker)
          }
          case _ => {
            newMarker.drop
          }
        }
        return true
      }
      case _ => {
        pathMarker.rollbackTo
        return false
      }
    }
  }
}