package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

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
      ScalaElementTypes.WRONGWAY
      var result: ScalaElementType = CompositeExpr.parse(builder)
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
        Def.parse(builder)
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

/*
    if (ScalaTokenTypes.kIMPORT.equals(builder.getTokenType)){
      Import.parse(builder)
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
*/
  }
}
