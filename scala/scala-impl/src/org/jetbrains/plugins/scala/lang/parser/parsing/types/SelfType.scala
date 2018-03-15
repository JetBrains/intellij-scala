package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

/**
* @author Alexander Podkhalyuzin
* Date: 08.02.2008
*/

/*
 * SelfType ::= id [':' Type] '=>' |
 *              ['this' | '_'] ':' Type '=>'
 */
object SelfType extends SelfType {
  override protected def infixType = InfixType
}

trait SelfType {
  protected def infixType: InfixType

  def parse(builder: ScalaPsiBuilder) {
    val selfTypeMarker = builder.mark
    
    def handleFunArrow() {
      builder.advanceLexer() //Ate '=>'
      selfTypeMarker.done(ScalaElementTypes.SELF_TYPE)
    }
    
    def handleColon() {
      builder.advanceLexer() //Ate ':'
      
      if (!parseType(builder)) selfTypeMarker.rollbackTo()
        else {
          builder.getTokenType match {
            case ScalaTokenTypes.tFUNTYPE => handleFunArrow()
            case _ => selfTypeMarker.rollbackTo()
          }
        }
    }
    
    def handleLastPart() {
      builder.getTokenType match {
        case ScalaTokenTypes.tCOLON => handleColon()
        case ScalaTokenTypes.tFUNTYPE => handleFunArrow()
        case _ => selfTypeMarker.rollbackTo()
      }
    }
    
    builder.getTokenType match {
      case ScalaTokenTypes.kTHIS | ScalaTokenTypes.tUNDER =>
        builder.advanceLexer() // Ate this or _
        builder.getTokenType match {
          case ScalaTokenTypes.tCOLON => handleColon()
          case _ => selfTypeMarker.rollbackTo()
        }
      case ScalaTokenTypes.tIDENTIFIER =>
        builder.advanceLexer() //Ate identifier
        handleLastPart()
      case ScalaTokenTypes.tLPARENTHESIS => 
         if (ParserUtils.parseBalancedParenthesis(builder, TokenSets.SELF_TYPE_ID))
           handleLastPart() else selfTypeMarker.rollbackTo()
      case _ => selfTypeMarker.rollbackTo()
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