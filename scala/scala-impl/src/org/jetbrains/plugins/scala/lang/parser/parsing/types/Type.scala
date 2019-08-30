package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.params.TypeParamClause

/**
 * @author Alexander Podkhalyuzin
 *         Date: 28.02.2008
 */

/*
 * Type ::= {'given'} InfixType '=>' Type
 *        | '(' ['=>' Type] ')' => Type
 *        | TypeParamClause '=>>' Type      (Scala 3+ only)
 *        | MatchType                       (Scala 3+ Only)
 *        | InfixType [ExistentialClause]
 *        | _ [>: Type] [<: Type]
 */
object Type extends Type {
  override protected def infixType = InfixType
}

trait Type extends ParsingRule {
  protected def infixType: InfixType

  override def parse()(implicit builder: ScalaPsiBuilder): Boolean = parse(builder, star = false, isPattern = false)

  def parse(builder: ScalaPsiBuilder, star: Boolean = false, isPattern: Boolean = false): Boolean = {
    val typeMarker = builder.mark
    implicit val ibuilder: ScalaPsiBuilder = builder

    if (FunType.parse()) {
      typeMarker.drop()
      true
    } else if (TypeParamClause.parse(builder, mayHaveContextBounds = false, mayHaveViewBounds = false)) {
      /** Scala 3+ Type Lambdas */
      builder.getTokenText match {
        case ScalaTokenType.TypeLambdaArrow.debugName =>
          builder.remapCurrentToken(ScalaTokenType.TypeLambdaArrow)
          builder.advanceLexer()
          if (!parse(builder, isPattern = isPattern)) {
            builder.error(ScalaBundle.message("wrong.type"))
          }
        case _ =>
          builder.error(ScalaBundle.message("type.lambda.expected"))
      }
      typeMarker.done(ScalaElementType.TYPE_LAMBDA)
      true
    } else {
      builder.getTokenType match {
        case ScalaTokenTypes.tUNDER =>
          builder.advanceLexer()
          builder.getTokenText match {
            case ">:" =>
              builder.advanceLexer()
              if (!parse()) {
                builder error ScalaBundle.message("wrong.type")
              }
            case _ => //nothing
          }
          builder.getTokenText match {
            case "<:" =>
              builder.advanceLexer()
              if (!parse()) {
                builder error ScalaBundle.message("wrong.type")
              }
            case _ => //nothing
          }
          typeMarker.done(ScalaElementType.WILDCARD_TYPE)
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
          true
        case ScalaTokenTypes.tIDENTIFIER if builder.getTokenText == "*" =>
          typeMarker.drop()
          true
        case _ =>
          typeMarker.drop()
          false
      }
    }
  }
}