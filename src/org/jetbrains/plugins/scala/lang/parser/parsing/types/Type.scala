package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.ScalaBundle
/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

/*
 * Type ::= InfixType '=>' Type
 *        | '(' ['=>' Type] ')' => Type
 *        | InfixType [ExistentialClause]
 *        | _ [>: Type] [<: Type]
 */

object Type {
  def parse(builder: PsiBuilder): Boolean = parse(builder,false)
  def parse(builder: PsiBuilder,star: Boolean): Boolean = parse(builder,star,false)
  def parse(builder: PsiBuilder,star: Boolean,isPattern: Boolean): Boolean = {
    val typeMarker = builder.mark
    builder.getTokenType match {
      /*case ScalaTokenTypes.tLPARENTHESIS => {
        val m1 = builder.mark
        builder.advanceLexer //Ate (
        val (_, tuple) = Types.parse(builder)
        if (builder.getTokenType == ScalaTokenTypes.tCOMMA) builder.advanceLexer
       builder.getTokenType match {
          case ScalaTokenTypes.tRPARENTHESIS => builder.advanceLexer
          case _ => builder error ScalaBundle.message("rparenthesis.expected")
        }
        m1.done(if (tuple) ScalaElementTypes.TUPLE_TYPE else ScalaElementTypes.TYPE_IN_PARENTHESIS)
      }*/
      case ScalaTokenTypes.tUNDER => {
        builder.advanceLexer()
        builder.getTokenText match {
          case ">:" => {
            builder.advanceLexer
            if (!Type.parse(builder)) {
              builder error ScalaBundle.message("wrong.type")
            }
          }
          case _ => {} //nothing
        }
        builder.getTokenText match {
          case "<:" => {
            builder.advanceLexer
            if (!Type.parse(builder)) {
              builder error ScalaBundle.message("wrong.type")
            }
          }
          case _ => {} //nothing
        }
        typeMarker.done(ScalaElementTypes.WILDCARD_TYPE)
        return true
      }
      case _ => if (!InfixType.parse(builder,star,isPattern)) {
        typeMarker.drop
        return false
      }
    }

    builder.getTokenType match {
      case ScalaTokenTypes.tFUNTYPE => {
        builder.advanceLexer //Ate =>
        if (!Type.parse(builder,false,isPattern)) {
          builder error ScalaBundle.message("wrong.type")
        }
        typeMarker.done(ScalaElementTypes.TYPE)
        return true
      }
      case ScalaTokenTypes.kFOR_SOME => {
        ExistentialClause parse builder
        typeMarker.done(ScalaElementTypes.EXISTENTIAL_TYPE)
        return true
      }
      case _ => {
        typeMarker.drop
        return true
      }
    }
  }
}