package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * @author Alexander Podkhalyuzin
 *         Date: 28.02.2008
 */

/*
 * Type ::= InfixType '=>' Type
 *        | '(' ['=>' Type] ')' => Type
 *        | InfixType [ExistentialClause]
 *        | _ [>: Type] [<: Type]
 */

object Type {
  def parse(builder: ScalaPsiBuilder, star: Boolean = false, isPattern: Boolean = false): Boolean = {
    val typeMarker = builder.mark
    if (!InfixType.parse(builder, star, isPattern)) {
      builder.getTokenType match {
        case ScalaTokenTypes.tUNDER =>
          builder.advanceLexer()
          builder.getTokenText match {
            case ">:" =>
              builder.advanceLexer()
              if (!Type.parse(builder)) {
                builder error ScalaBundle.message("wrong.type")
              }
            case _ => //nothing
          }
          builder.getTokenText match {
            case "<:" =>
              builder.advanceLexer()
              if (!Type.parse(builder)) {
                builder error ScalaBundle.message("wrong.type")
              }
            case _ => //nothing
          }
          typeMarker.done(ScalaElementTypes.WILDCARD_TYPE)
          builder.getTokenType match {
            case ScalaTokenTypes.tFUNTYPE =>
              val funMarker = typeMarker.precede()
              builder.advanceLexer() //Ate =>
              if (!Type.parse(builder, star = false, isPattern = isPattern)) {
                builder error ScalaBundle.message("wrong.type")
              }
              funMarker.done(ScalaElementTypes.TYPE)
            case _ =>
          }
          return true
        case _ =>
          typeMarker.drop()
          return false
      }
    }


    builder.getTokenType match {
      case ScalaTokenTypes.tFUNTYPE =>
        builder.advanceLexer() //Ate =>
        if (!Type.parse(builder, star = false, isPattern = isPattern)) {
          builder error ScalaBundle.message("wrong.type")
        }
        typeMarker.done(ScalaElementTypes.TYPE)
      case ScalaTokenTypes.kFOR_SOME =>
        ExistentialClause parse builder
        typeMarker.done(ScalaElementTypes.EXISTENTIAL_TYPE)
      case _ => typeMarker.drop()
    }
    true
  }
}