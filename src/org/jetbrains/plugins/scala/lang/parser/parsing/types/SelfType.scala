package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 08.02.2008
*/

/*
 * SelfType ::= id [':' Type] '=>' |
 *              ['this' | '_'] ':' Type '=>'
 */
object SelfType extends SelfType {
  override protected val infixType = InfixType
}

trait SelfType {
  protected val infixType: InfixType

  def parse(builder: ScalaPsiBuilder) {
    val selfTypeMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kTHIS | ScalaTokenTypes.tUNDER =>
        builder.advanceLexer // Ate this or _
        builder.getTokenType match {
          case ScalaTokenTypes.tCOLON => {
            builder.advanceLexer //Ate ':'
            if (!parseType(builder)) {
              selfTypeMarker.rollbackTo
              return
            }
            else {
              builder.getTokenType match {
                case ScalaTokenTypes.tFUNTYPE => {
                  builder.advanceLexer //Ate '=>'
                  selfTypeMarker.done(ScalaElementTypes.SELF_TYPE)
                  return
                }
                case _ => {
                  selfTypeMarker.rollbackTo
                  return
                }
              }
            }
          }
          case _ => {
            selfTypeMarker.rollbackTo
            return
          }
        }
      case ScalaTokenTypes.tIDENTIFIER =>
        builder.advanceLexer //Ate identifier
        builder.getTokenType match {
          case ScalaTokenTypes.tCOLON => {
            builder.advanceLexer //Ate ':'
            if (!parseType(builder)) {
              selfTypeMarker.rollbackTo
              return
            }
            else {
              builder.getTokenType match {
                case ScalaTokenTypes.tFUNTYPE => {
                  builder.advanceLexer //Ate '=>'
                  selfTypeMarker.done(ScalaElementTypes.SELF_TYPE)
                  return
                }
                case _ => {
                  selfTypeMarker.rollbackTo
                  return
                }
              }
            }
          }
          case ScalaTokenTypes.tFUNTYPE => {
            builder.advanceLexer //Ate '=>'
            selfTypeMarker.done(ScalaElementTypes.SELF_TYPE)
            return
          }
          case _ => {
            selfTypeMarker.rollbackTo
            return
          }
        }
      case _ =>
        selfTypeMarker.rollbackTo
        return
    }
  }

  def parseType(builder : ScalaPsiBuilder) : Boolean = {
    val typeMarker = builder.mark
    if (!infixType.parse(builder, star = false, isPattern = true)) {
      typeMarker.drop()
      return false
    }

    builder.getTokenType match {
      case ScalaTokenTypes.kFOR_SOME =>
        ExistentialClause parse builder
        typeMarker.done(ScalaElementTypes.EXISTENTIAL_TYPE)
      case _ => typeMarker.drop()
    }
    true
  }
}