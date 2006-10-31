package org.jetbrains.plugins.scala.lang.parser.parsing.expressions{
/**
* @author Ilya Sergey
*/
import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._

  object BlockExpr {
  /*
  Block expression
  Default grammar
  BlockExpr ::= ‘{’ CaseClauses ‘}’
                | ‘{’ Block ‘}’
  */
    def parse(builder : PsiBuilder) : ScalaElementType = {
      val blockExprMarker = builder.mark()

      if (builder.getTokenType.eq(ScalaTokenTypes.tLBRACE)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tLBRACE)
        ParserUtils.rollForward(builder)
        if (builder.getTokenType.eq(ScalaTokenTypes.tRBRACE)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.tRBRACE)
          blockExprMarker.done(ScalaElementTypes.BLOCK_EXPR)
          ScalaElementTypes.BLOCK_EXPR
        } else {
          var result = Block.parse(builder)
          if (result.equals(ScalaElementTypes.BLOCK)) {
            ParserUtils.rollForward(builder)
            if (builder.getTokenType.eq(ScalaTokenTypes.tRBRACE)){
              ParserUtils.eatElement(builder, ScalaTokenTypes.tRBRACE)
              blockExprMarker.done(ScalaElementTypes.BLOCK_EXPR)
              ScalaElementTypes.BLOCK_EXPR
            } else {
              builder.error("} expected")
              blockExprMarker.done(ScalaElementTypes.BLOCK_EXPR)
              ScalaElementTypes.BLOCK_EXPR
            }
          } else {
            builder.error("Wrong inner block statement")
            blockExprMarker.done(ScalaElementTypes.BLOCK_EXPR)
            ScalaElementTypes.BLOCK_EXPR
          }
        }
      } else {
         blockExprMarker.rollbackTo()
         ScalaElementTypes.WRONGWAY
      }
    }
  }

  object Block {
  /*
  Block
  Default grammar
  Block ::= {BlockStat StatementSeparator} [ResultExpr]
  */
    def parse(builder : PsiBuilder) : ScalaElementType = {

      def rollForward = {
        var flag1 = true
        while ( !builder.eof() && flag1){
           builder.getTokenType match{
             case ScalaTokenTypes.tLINE_TERMINATOR
                | ScalaTokenTypes.tSEMICOLON => builder.advanceLexer
             case _ => flag1 = false
           }
        }
      }

      var rollbackMarker = builder.mark()
      var result = ScalaElementTypes.WRONGWAY
      var flag = false
      do {
        rollForward
        result = BlockStat.parse(builder)
        if (result.eq(ScalaElementTypes.BLOCK_STAT)) {
          rollbackMarker.drop()
          rollForward
          rollbackMarker = builder.mark()
          builder.getTokenType match {
            case ScalaTokenTypes.tRBRACE => {
              rollbackMarker.drop()
              result = ScalaElementTypes.BLOCK
              flag = false
            }
            case _ => {
              flag = true
            }
          }
        } else {
          flag = false
          rollbackMarker.rollbackTo()
          result = ScalaElementTypes.WRONGWAY
        }
      } while (flag)
      result
    }
  }

  /**
  BLOCK STATEMENTS
  BlockStat ::= Import
            | [implicit] Def
            | {LocalModifier} TmplDef
            | Expr1
            |
  **/
  object BlockStat {

    def parse(builder : PsiBuilder) : ScalaElementType = {
      val blockStatMarker = builder.mark()
      var result = CompositeExpr.parse(builder)
      if (result == ScalaElementTypes.EXPR1) {
        blockStatMarker.done(ScalaElementTypes.BLOCK_STAT)
        ScalaElementTypes.BLOCK_STAT
      }
      else {
        blockStatMarker.rollbackTo
        ScalaElementTypes.WRONGWAY
      }
    }

  }





}

