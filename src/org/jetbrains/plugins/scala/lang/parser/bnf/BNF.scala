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

  //fix problem with implicit
  val tMODIFIERS: TokenSet = TokenSet.create(
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

  val tTMPLDEF: TokenSet = TokenSet.create(
    Array(
        ScalaTokenTypes.kCLASS,
        ScalaTokenTypes.kOBJECT,
        ScalaTokenTypes.kTRAIT
    )
  )

}