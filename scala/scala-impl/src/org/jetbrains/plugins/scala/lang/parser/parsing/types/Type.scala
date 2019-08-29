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

  override def parse()(implicit builder: ScalaPsiBuilder): Boolean = parse(star = false, isPattern = false)

  def parse(star: Boolean = false, isPattern: Boolean = false)(implicit builder: ScalaPsiBuilder): Boolean = {
    val typeMarker = builder.mark

    val isImplicitFunctionType =
      builder.getTokenText match {
        case ScalaTokenType.Given.debugName =>
          builder.remapCurrentToken(ScalaTokenType.Given)
          builder.advanceLexer()
          true
        case _ => false
      }

    if (infixType.parse(builder, star, isPattern)) {
      builder.getTokenType match {
        case ScalaTokenTypes.tFUNTYPE =>
          builder.advanceLexer() //Ate =>
          if (!parse(isPattern = isPattern)) {
            builder.error(ScalaBundle.message("wrong.type"))
          }
          typeMarker.done(ScalaElementType.TYPE)
        case _ if isImplicitFunctionType =>
          builder.error(ScalaBundle.message("fun.sign.expected"))
          typeMarker.done(ScalaElementType.TYPE)
        case ScalaTokenTypes.kFOR_SOME =>
          ExistentialClause parse builder
          typeMarker.done(ScalaElementType.EXISTENTIAL_TYPE)
        case _ => typeMarker.drop()
      }
      true
    } else if (isImplicitFunctionType) {
      typeMarker.drop()
      false
    } else if (TypeParamClause.parse(builder, mayHaveContextBounds = false, mayHaveViewBounds = false)) {
      /** Scala 3+ Type Lambdas */
      builder.getTokenText match {
        case ScalaTokenType.TypeLambdaArrow.debugName =>
          builder.remapCurrentToken(ScalaTokenType.TypeLambdaArrow)
          builder.advanceLexer()
          if (!parse(isPattern = isPattern)) {
            builder.error(ScalaBundle.message("wrong.type"))
          }
          typeMarker.done(ScalaElementType.TYPE_LAMBDA)
        case _ => builder.error(ScalaBundle.message("type.lambda.expected"))
      }
      true
    } else if (MatchType.parse()) {
      typeMarker.drop()
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
              if (!parse(isPattern = isPattern)) {
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