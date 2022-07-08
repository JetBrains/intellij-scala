package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

/*
 * SelfType ::= id [':' Type] '=>' |
 *              ['this' | '_'] ':' Type '=>'
 */
object SelfType extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val selfTypeMarker = builder.mark()
    
    def handleFunArrow(): Unit = {
      builder.advanceLexer() //Ate '=>'
      selfTypeMarker.done(ScalaElementType.SELF_TYPE)
    }
    
    def handleColon(): Unit = {
      builder.advanceLexer() //Ate ':'
      
      if (!parseType()) selfTypeMarker.rollbackTo()
        else {
          builder.getTokenType match {
            case ScalaTokenTypes.tFUNTYPE => handleFunArrow()
            case _ => selfTypeMarker.rollbackTo()
          }
        }
    }
    
    def handleLastPart(): Unit = {
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
         if (ParserUtils.parseBalancedParenthesis(TokenSets.SELF_TYPE_ID))
           handleLastPart() else selfTypeMarker.rollbackTo()
      case _ => selfTypeMarker.rollbackTo()
    }
    true
  }

  private def parseType()(implicit builder : ScalaPsiBuilder) : Boolean = {
    val typeMarker = builder.mark()
    if (!InfixType(isPattern = true)) {
      typeMarker.drop()
      return false
    }

    builder.getTokenType match {
      case ScalaTokenTypes.kFOR_SOME =>
        ExistentialClause()
        typeMarker.done(ScalaElementType.EXISTENTIAL_TYPE)
      case _ => typeMarker.drop()
    }
    true
  }
}