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
  val templateStatKeywords = TokenSet.create(
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

  val firstDclDef = TokenSet.orSet(
    Array (templateStatKeywords, firstTmplDef)
  )

  val firstLocalModifier = TokenSet.create(
    Array (
      ScalaTokenTypes.kABSTRACT,
      ScalaTokenTypes.kFINAL,
      ScalaTokenTypes.kIMPLICIT,
      ScalaTokenTypes.kSEALED
    )
  )

  val firstAccessModifier : TokenSet = TokenSet.create(
    Array (
      ScalaTokenTypes.kPRIVATE,
      ScalaTokenTypes.kPROTECTED
    )
  )
  //fix problem with implicit
  val firstModifier : TokenSet = TokenSet.orSet(
    Array (
        TokenSet.create(Array(ScalaTokenTypes.kOVERRIDE)),
        firstAccessModifier,
        firstLocalModifier
    )
  )

  val firstAttributeClause : TokenSet = TokenSet.create(
    Array(
      ScalaTokenTypes.tLSQBRACKET
    )
  )

  val firstImport = TokenSet.create(
    Array(
      ScalaTokenTypes.kIMPORT
    )
  )



  //todo: add first(Expression)
  val firstExpr : TokenSet = TokenSet.create(
    Array (
       ScalaTokenTypes.tINTEGER,
       ScalaTokenTypes.tFLOAT,
       ScalaTokenTypes.kTRUE,
       ScalaTokenTypes.kFALSE,
       ScalaTokenTypes.tCHAR,
       ScalaTokenTypes.kNULL,
       ScalaTokenTypes.tSTRING_BEGIN,
       ScalaTokenTypes.tPLUS,
       ScalaTokenTypes.tMINUS,
       ScalaTokenTypes.tTILDA,
       ScalaTokenTypes.tNOT,
       ScalaTokenTypes.tIDENTIFIER,
       ScalaTokenTypes.tLBRACE,
       ScalaTokenTypes.kNEW
    )
  )

  val firstLineTerminate :  TokenSet = TokenSet.create(
    Array (
      ScalaTokenTypes.tLINE_TERMINATOR
    )
  )

  val firstTemplateStat : TokenSet = TokenSet.orSet(
    Array (
      firstImport,
      firstAttributeClause,
      firstModifier,
      firstDclDef,
      firstExpr
      //firstLineTerminate
    )
  )

  //todo: add first(Type)
  val firstType : TokenSet = TokenSet.create(
    Array (
      ScalaTokenTypes.tIDENTIFIER
    )
  )

  val firstFunSig : TokenSet = TokenSet.create(
    Array (
      ScalaTokenTypes.tIDENTIFIER
    )
  )

  val firstFunDef = firstFunSig 

  val firstStatementSeparator = TokenSet.create(
    Array (
      ScalaTokenTypes.tLINE_TERMINATOR,
      ScalaTokenTypes.tSEMICOLON
    )
  )

  val firstTypeParam = TokenSet.create(
    Array (
      ScalaTokenTypes.tIDENTIFIER
    )
  )

  val firstParamClause = TokenSet.create(
    Array (
      ScalaTokenTypes.tLINE_TERMINATOR,
      ScalaTokenTypes.tLPARENTHIS
    )
  )

  val firstParamType = TokenSet.orSet(
    Array (
      firstType,
      TokenSet.create(
        Array (
          ScalaTokenTypes.tFUNTYPE
        )
      )
    )
  )

  val firstParam = TokenSet.create(
    Array (
      ScalaTokenTypes.tIDENTIFIER
    )
  )

  val firstClassParam = TokenSet.orSet(
    Array(
      firstModifier,
      TokenSet.create(
        Array (
          ScalaTokenTypes.tIDENTIFIER,
          ScalaTokenTypes.kVAR,
          ScalaTokenTypes.kVAL
        )
      ),
      firstParam
    )
  )
}