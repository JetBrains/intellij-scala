package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.{CompoundType, Type}

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * ResultExpr ::= Expr1
 *              | (Bindings | id ':' CompoundType) '=>' Block
 */
object ResultExpr extends ResultExpr {
  override protected val bindings = Bindings
  override protected val `type` = CompoundType
  override protected val block = Block
}

trait ResultExpr {
  protected val bindings: Bindings
  protected val `type`: Type
  protected val block: Block

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val resultMarker = builder.mark
    val backupMarker = builder.mark

    def parseFunctionEnd() = builder.getTokenType match {
      case ScalaTokenTypes.tFUNTYPE =>
        builder.advanceLexer() //Ate =>
        block parse(builder, hasBrace = false, needNode = true)
        backupMarker.drop()
        resultMarker.done(ScalaElementTypes.FUNCTION_EXPR)
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
        val pt = builder.mark
        `type`.parse(builder, isPattern = false)
        pt.done(ScalaElementTypes.PARAM_TYPE)
      }
      builder.getTokenType match {
        case ScalaTokenTypes.tFUNTYPE =>
          val psm = paramsMarker.precede // 'parameter list'
          paramMarker.done(ScalaElementTypes.PARAM)
          paramsMarker.done(ScalaElementTypes.PARAM_CLAUSE)
          psm.done(ScalaElementTypes.PARAM_CLAUSES)

          return parseFunctionEnd()
        case _ =>
          builder error ErrMsg("fun.sign.expected")
      }
      parseFunctionEnd()
    }

    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS =>
        bindings parse builder
        return parseFunctionEnd()
      case ScalaTokenTypes.kIMPLICIT =>
        val pmarker = builder.mark()
        builder.advanceLexer() //ate implicit
        builder.getTokenType match {
          case ScalaTokenTypes.tIDENTIFIER =>
            return parseFunction(pmarker)
          case _ =>
            resultMarker.drop()
            backupMarker.rollbackTo()
            return false
        }
      case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.tUNDER =>
        val pmarker = builder.mark
        return parseFunction(pmarker)
      case _ =>
        backupMarker.drop()
    }
    resultMarker.drop()
    false
  }
}