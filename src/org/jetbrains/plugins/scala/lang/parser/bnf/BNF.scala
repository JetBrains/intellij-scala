package org.jetbrains.plugins.scala.lang.parser.bnf

import com.intellij.psi.tree.TokenSet, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes


object BNF {

  val tLITERALS: TokenSet = TokenSet.create(
    Array( ScalaTokenTypes.tINTEGER,
           ScalaTokenTypes.tFLOAT,
           ScalaTokenTypes.kTRUE,
           ScalaTokenTypes.kFALSE,
           ScalaTokenTypes.tCHAR,
           ScalaTokenTypes.kNULL,
           ScalaTokenTypes.tSTRING_BEGIN
    )
  )
  
  val tSIMPLE_FIRST: TokenSet = TokenSet.create(
    Array( ScalaTokenTypes.tIDENTIFIER,
           ScalaTokenTypes.kTHIS,
           ScalaTokenTypes.kSUPER,
           ScalaTokenTypes.tLPARENTHIS,
           ScalaTokenTypes.kNEW
    )
  )

  val tPREFIXES: TokenSet = TokenSet.create(
    Array(
      ScalaTokenTypes.tPLUS,
      ScalaTokenTypes.tMINUS,
      ScalaTokenTypes.tTILDA,
      ScalaTokenTypes.tNOT
    )
  )


  /********************************************************/
  /*********************** FIRSTS *************************/
  /********************************************************/
  private val templateStatKeywords = TokenSet.create(
    Array(
      ScalaTokenTypes.kVAL,
      ScalaTokenTypes.kVAR,
      ScalaTokenTypes.kDEF,
      ScalaTokenTypes.kTYPE
    )
  )

  val firstTmplDef = TokenSet.create(
    Array(
      ScalaTokenTypes.kCASE,
      ScalaTokenTypes.kCLASS,
      ScalaTokenTypes.kOBJECT,
      ScalaTokenTypes.kTRAIT
    )
  )

  val firstDef = TokenSet.orSet(
    Array (templateStatKeywords, firstTmplDef)
  )

  //fix problem with implicit
  val firstModifier: TokenSet = TokenSet.create(
    Array(
        ScalaTokenTypes.kABSTRACT ,
        ScalaTokenTypes.kFINAL    ,
        ScalaTokenTypes.kIMPLICIT,
        ScalaTokenTypes.kOVERRIDE ,
        ScalaTokenTypes.kPRIVATE  ,
        ScalaTokenTypes.kPROTECTED,
        ScalaTokenTypes.kSEALED
    )
  )

  val firstAttributeClause = TokenSet.create(
    Array(
      ScalaTokenTypes.tLSQBRACKET
    )
  )

  val firstDcl = templateStatKeywords

  val firstImport = TokenSet.create(
    Array(
      ScalaTokenTypes.kIMPORT
    )
  )

    //add firstExpr
  val firstTemplateStat : TokenSet = TokenSet.orSet(
    Array (firstImport, firstAttributeClause, firstModifier, firstDef, firstDcl)
  )

  //todo: add first(Expression)
  val firstExpr : TokenSet = TokenSet.create(
    Array (
      ScalaTokenTypes.tIDENTIFIER,
      ScalaTokenTypes.tINTEGER
    )
  )
}