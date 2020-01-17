package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/*
 * Type ::= InfixType '=>' Type
 *        | '(' ['=>' Type] ')' => Type
 *        | InfixType [ExistentialClause]
 *        | _ SubtypeBounds
 * SubtypeBounds : == [>: Type] [<: Type]
 */
object Type extends Type {
  override protected def infixType = InfixType
}

trait Type {
  protected def infixType: InfixType

  def parse(builder: ScalaPsiBuilder, star: Boolean = false, isPattern: Boolean = false): Boolean = {
    implicit val b: ScalaPsiBuilder = builder
    val typeMarker = builder.mark
    if (!infixType.parse(builder, star, isPattern)) {
      builder.getTokenType match {
        case ScalaTokenTypes.tUNDER =>
          builder.advanceLexer()
          Bounds.parseSubtypeBounds()
          typeMarker.done(ScalaElementType.WILDCARD_TYPE)

          // TODO: looks like this is a dead code, no tests trigger breakpoint inside, leaving it just in case...
          builder.getTokenType match {
            case ScalaTokenTypes.tFUNTYPE =>
              val funMarker = typeMarker.precede()
              builder.advanceLexer() //Ate =>
              if (!parse(builder, isPattern = isPattern)) {
                builder error ScalaBundle.message("wrong.type")
              }
              funMarker.done(ScalaElementType.TYPE)
            case _ =>
          }
          return true
        case ScalaTokenTypes.tIDENTIFIER if builder.getTokenText == "*" =>
          typeMarker.drop()
          return true
        case _ =>
          typeMarker.drop()
          return false
      }
    }


    builder.getTokenType match {
      case ScalaTokenTypes.tFUNTYPE =>
        builder.advanceLexer() //Ate =>
        if (!parse(builder, isPattern = isPattern)) {
          builder.error(ScalaBundle.message("wrong.type"))
        }
        typeMarker.done(ScalaElementType.TYPE)
      case ScalaTokenTypes.kFOR_SOME =>
        ExistentialClause parse builder
        typeMarker.done(ScalaElementType.EXISTENTIAL_TYPE)
      case _ =>
        typeMarker.drop()
    }
    true
  }
}