package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.CompoundType
import org.jetbrains.plugins.scala.lang.parser.util.InScala3

/*
 * ResultExpr ::=  Expr1
 *              |  (Bindings | id ':' CompoundType) '=>' Block
 *
 * In Scala 3:
 *
 * BlockResult ::=  [‘implicit’] FunParams ‘=>’ Block
 *               |  Expr1
 */
object ResultExpr {
  def apply(stopOnOutdent: Boolean)(implicit builder: ScalaPsiBuilder): Boolean = {
    val resultMarker = builder.mark()
    val backupMarker = builder.mark()

    def parseFunctionEnd(): Boolean = builder.getTokenType match {
      case ScalaTokenTypes.tFUNTYPE | ScalaTokenType.ImplicitFunctionArrow =>
        builder.advanceLexer() //Ate => or ?=>
        Block.Braceless(stopOnOutdent, needNode = true)
        backupMarker.drop()
        resultMarker.done(ScalaElementType.FUNCTION_EXPR)
        true
      case _ =>
        resultMarker.drop()
        backupMarker.rollbackTo()
        false
    }

    def parseFunction(paramsMarker: PsiBuilder.Marker): Boolean = {
      val paramMarker = builder.mark()
      builder.advanceLexer() //Ate id
      if (ScalaTokenTypes.tCOLON == builder.getTokenType) {
        builder.advanceLexer() // ate ':'
        val pt = builder.mark()
        CompoundType()
        pt.done(ScalaElementType.PARAM_TYPE)
      }
      builder.getTokenType match {
        case ScalaTokenTypes.tFUNTYPE | ScalaTokenType.ImplicitFunctionArrow =>
          val psm = paramsMarker.precede // 'parameter list'
          paramMarker.done(ScalaElementType.PARAM)
          paramsMarker.done(ScalaElementType.PARAM_CLAUSE)
          psm.done(ScalaElementType.PARAM_CLAUSES)

          return parseFunctionEnd()
        case _ =>
          builder error ErrMsg("fun.sign.expected")
      }
      parseFunctionEnd()
    }

    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS =>
        Bindings()
        return parseFunctionEnd()
      case ScalaTokenTypes.kIMPLICIT =>
        val pmarker = builder.mark()
        builder.advanceLexer() //ate implicit
        builder.getTokenType match {
          case ScalaTokenTypes.tIDENTIFIER =>
            return parseFunction(pmarker)
          case InScala3(ScalaTokenTypes.tLPARENTHESIS) =>
            pmarker.drop()
            Bindings()
            return parseFunctionEnd()
          case _ =>
            resultMarker.drop()
            backupMarker.rollbackTo()
            return false
        }
      case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.tUNDER =>
        val pmarker = builder.mark()
        return parseFunction(pmarker)
      case _ =>
        backupMarker.drop()
    }
    resultMarker.drop()
    false
  }
}