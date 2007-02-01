package org.jetbrains.plugins.scala.lang.parser.parsing.expressions
/**
* @author Ilya Sergey
*/
import _root_.scala.collection.mutable._

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.util._
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns._
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template._
import org.jetbrains.plugins.scala.lang.parser.parsing.base._
import org.jetbrains.plugins.scala.lang.parser.parsing.top._



object BlockExpr {
  /*
  Block expression
  Default grammar
  BlockExpr ::= ‘{’ CaseClauses ‘}’
                | ‘{’ Block ‘}’
  */
  def parse(builder: PsiBuilder): ScalaElementType = {
    val blockExprMarker = builder.mark()

    if (ScalaTokenTypes.tLBRACE.equals(builder.getTokenType)) {
      val um = builder.mark()
      ParserUtils.eatElement(builder, ScalaTokenTypes.tLBRACE)
      if (ScalaTokenTypes.tRBRACE.equals(builder.getTokenType)){
        ParserUtils.eatElement(builder, ScalaTokenTypes.tRBRACE)
        um.done(ScalaElementTypes.UNIT)
        blockExprMarker.drop()
        ScalaElementTypes.BLOCK_EXPR
      } else if ({
        um.drop()
        ! ScalaTokenTypes.kCASE.equals(builder.getTokenType)
      }){

        /*  ‘{’ Block ‘}’ */

        var result = Block.parse(builder, true)
        if (result.equals(ScalaElementTypes.BLOCK)) {
          ParserUtils.rollForward(builder)
          if (ScalaTokenTypes.tRBRACE.equals(builder.getTokenType)){
            ParserUtils.eatElement(builder, ScalaTokenTypes.tRBRACE)
            blockExprMarker.done(ScalaElementTypes.BLOCK_EXPR)
            ScalaElementTypes.BLOCK_EXPR
          } else {
            builder.error("} expected")
            ParserUtils.rollPanicToBrace(builder, ScalaTokenTypes.tLBRACE, ScalaTokenTypes.tRBRACE)
            blockExprMarker.done(ScalaElementTypes.BLOCK_EXPR)
            ScalaElementTypes.BLOCK_EXPR
          }
        } else {
          var errMarker = builder.mark()
          ParserUtils.rollPanicToBrace(builder, ScalaTokenTypes.tLBRACE, ScalaTokenTypes.tRBRACE)
          errMarker.error("Wrong inner block statement")
          blockExprMarker.done(ScalaElementTypes.BLOCK_EXPR)
          ScalaElementTypes.BLOCK_EXPR
        }
      } else {
        /* ‘{’ CaseClauses ‘}’ */
        var result = CaseClauses.parse(builder)
        if (result.equals(ScalaElementTypes.CASE_CLAUSES)) {
          if (ScalaTokenTypes.tRBRACE.equals(builder.getTokenType)){
            ParserUtils.eatElement(builder, ScalaTokenTypes.tRBRACE)
            blockExprMarker.done(ScalaElementTypes.BLOCK_EXPR)
            ScalaElementTypes.BLOCK_EXPR
          } else {
            builder.error("} expected")
            ParserUtils.rollPanicToBrace(builder, ScalaTokenTypes.tLBRACE, ScalaTokenTypes.tRBRACE)
            blockExprMarker.done(ScalaElementTypes.BLOCK_EXPR)
            ScalaElementTypes.BLOCK_EXPR
          }
        } else {
          ParserUtils.rollPanicToBrace(builder, ScalaTokenTypes.tLBRACE, ScalaTokenTypes.tRBRACE)
          blockExprMarker.error("Wrong inner block statement")
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

  /*
    Moreover, in this case we'll consider a tuple case
  */

  def parse(builder: PsiBuilder, withBrace: Boolean): ScalaElementType = {

    // TODO
    val elems = new HashSet[IElementType]
    elems += ScalaTokenTypes.tLINE_TERMINATOR
    elems += ScalaTokenTypes.tSEMICOLON
    elems += ScalaTokenTypes.tRBRACE


    def rollForward: Boolean = {
      var flag1 = true
      var flag2 = false
      while (flag1 && ! builder.eof()){
        builder.getTokenType match {
          case ScalaTokenTypes.tLINE_TERMINATOR
          | ScalaTokenTypes.tSEMICOLON => {
            builder.advanceLexer
            //ParserUtils.eatElement(builder, builder.getTokenType())
            flag2 = true
          }
          case _ => flag1 = false
        }
      }
      flag2
    }

    var counter = 0
    val blockMarker = builder.mark()
    var rollbackMarker = builder.mark()

    val tupleMarker = builder.mark()
    var bmAlive = true
    var tmAlive = true
    var result = ScalaElementTypes.BLOCK


    def tupleParse: Unit = {
      tmAlive = false
      if (ScalaTokenTypes.tCOMMA.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tCOMMA)
        val res = CompositeExpr.parse(builder)
        if (ScalaElementTypes.EXPR1.equals(res)) {
          builder.getTokenType match {
            case ScalaTokenTypes.tCOMMA => tupleParse
            case ScalaTokenTypes.tRBRACE => tupleMarker.done(ScalaElementTypes.TUPLE)
            case _ => {
              builder.error(", or } expected")
              tupleMarker.done(ScalaElementTypes.TUPLE)
            }
          }
        } else {
          tupleMarker.done(ScalaElementTypes.TUPLE)
        }
      } else {
        tupleMarker.done(ScalaElementTypes.TUPLE)
      }
    }

    var flag = false
    var flag2 = true
    rollForward
    do {
      result = BlockStat.parse(builder)
      if (flag2 &&
      (result.equals(ScalaElementTypes.BLOCK_STAT) ||
      result.equals(ScalaElementTypes.EXPR1))) {
        counter = counter + 1
        if (ScalaTokenTypes.tCOMMA.equals(builder.getTokenType) &&
        result.equals(ScalaElementTypes.EXPR1)){
          rollbackMarker.drop()
          blockMarker.drop()
          bmAlive = false
          flag = false
          tupleParse
          result = ScalaElementTypes.BLOCK
        } else if ({
          if (tmAlive) {
            tupleMarker.drop()
            tmAlive = false
          }
          ScalaTokenTypes.tFUNTYPE.equals(builder.getTokenType) &&
          result.equals(ScalaElementTypes.EXPR1)
        }) {
          rollbackMarker.rollbackTo()
          flag = false
          ResultExpr.parse(builder)
          result = ScalaElementTypes.BLOCK
        } else {
          rollbackMarker.drop()
          flag2 = rollForward
          rollbackMarker = builder.mark()
          builder.getTokenType match {
            case ScalaTokenTypes.tRBRACE if withBrace => {
              rollbackMarker.drop()
              result = ScalaElementTypes.BLOCK
              flag = false
            }
            case _ => {
              flag = true
            }
          }
        }
      } else if ({
        rollbackMarker.rollbackTo()
        result = ResultExpr.parse(builder)
        rollbackMarker = builder.mark()
        ! ScalaElementTypes.WRONGWAY.equals(result) && flag2
      }) {
        flag = false
        result = ScalaElementTypes.BLOCK
        rollbackMarker.drop()
      } else if (! withBrace) {
        flag = false
        rollbackMarker.rollbackTo()
        result = ScalaElementTypes.BLOCK
      } else {
        flag = false
        if (counter == 0) {
          rollbackMarker.rollbackTo()
          result = ScalaElementTypes.WRONGWAY
        } else {
          builder.error("Wrong statement")
          rollbackMarker.drop()
          // from 27.12.2006
          //result = ScalaElementTypes.BLOCK
          result = ScalaElementTypes.WRONGWAY
        }
      }
    } while (flag)
    if (bmAlive) {
      blockMarker.done(ScalaElementTypes.BLOCK)
    }
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

  def parse(builder: PsiBuilder): ScalaElementType = {
    val blockStatMarker = builder.mark()

    /* Expr1 */
    def parseExpr1: ScalaElementType = {
      var result = CompositeExpr.parse(builder)
      if (! (result == ScalaElementTypes.WRONGWAY)) {
        blockStatMarker.drop
        result
      }
      else {
        blockStatMarker.rollbackTo()
        ScalaElementTypes.BLOCK_STAT
      }
    }

    /* Def */
    def parseDef(isImplicit: Boolean): ScalaElementType = {
      var rbMarker = builder.mark()
      var first = builder.getTokenType
      builder.advanceLexer
      ParserUtils.rollForward(builder)
      var second = builder.getTokenType
      rbMarker.rollbackTo()
      if (ScalaTokenTypes.kCASE.equals(first) &&
      (ScalaTokenTypes.kCLASS.equals(second) ||
      ScalaTokenTypes.kOBJECT.equals(second) ||
      ScalaTokenTypes.kTRAIT.equals(second))){
        Def.parseBody(builder)
        blockStatMarker.drop
        ScalaElementTypes.BLOCK_STAT
      } else if (BNF.firstDef.contains(builder.getTokenType) &&
      ! ScalaTokenTypes.kCASE.equals(builder.getTokenType)) {
        Def.parseBody(builder)
        blockStatMarker.drop
        ScalaElementTypes.BLOCK_STAT
      } else if (! isImplicit)  {
        parseExpr1
      } else {
        builder.error("Definition expected")
        blockStatMarker.rollbackTo
        ScalaElementTypes.WRONGWAY
      }
    }                             

    if (ScalaTokenTypes.kIMPORT.equals(builder.getTokenType)){
      Import.parseBody(builder)
      blockStatMarker.drop
      ScalaElementTypes.BLOCK_STAT
    } else if (builder.getTokenType != null && BNF.firstLocalModifier.contains(builder.getTokenType)) {
      var localModSet = new HashSet[IElementType]
      while (builder.getTokenType != null &&
      BNF.firstLocalModifier.contains(builder.getTokenType) &&
      ! localModSet.contains(builder.getTokenType)){
        localModSet += builder.getTokenType
        builder.advanceLexer
      }
      if (builder.getTokenType != null && BNF.firstLocalModifier.contains(builder.getTokenType)){
        builder.error("Repeated modifier!")
        blockStatMarker.drop
        ScalaElementTypes.BLOCK_STAT
      } else parseDef(true)
    } else if (builder.getTokenType != null && ScalaTokenTypes.kIMPLICIT.equals(builder.getTokenType)){
      builder.advanceLexer
      parseDef(true)
    } else {
      parseDef(false)
    }
  }
}
