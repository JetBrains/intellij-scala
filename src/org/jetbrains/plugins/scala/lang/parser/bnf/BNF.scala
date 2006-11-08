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

    val firstStableId : TokenSet = TokenSet.create(
    Array (
       ScalaTokenTypes.kTHIS,
       ScalaTokenTypes.kSUPER,
       ScalaTokenTypes.tIDENTIFIER
    )
  )

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

   val firstStatementSeparator = TokenSet.create(
    Array (
      ScalaTokenTypes.tLINE_TERMINATOR,
      ScalaTokenTypes.tSEMICOLON
    )
  )

  //todo: add first(Type)
  val firstType : TokenSet = TokenSet.create(
    Array (
      ScalaTokenTypes.tIDENTIFIER
    )
  )

  /********************************************************************************************/
  /********************************* Import, Attribute and Modifier ***************************/
  /********************************************************************************************/

  val firstImport = TokenSet.create(
    Array(
      ScalaTokenTypes.kIMPORT
    )
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
      TokenSet.create(
        Array(
          ScalaTokenTypes.kOVERRIDE
        )
      ),
      firstAccessModifier,
      firstLocalModifier
    )
  )

  val firstAttributeClause : TokenSet = TokenSet.create(
    Array(
      ScalaTokenTypes.tLSQBRACKET
    )
  )


  /************* parameters ***************/

  val firstTypeParam = TokenSet.create(
    Array (
      ScalaTokenTypes.tIDENTIFIER
    )
  )

  val firstFunTypeParam = TokenSet.create(
    Array (
      ScalaTokenTypes.tLINE_TERMINATOR,
      ScalaTokenTypes.tLSQBRACKET
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

  /*******************************************************************************/
  /********************************** Def and Dcl ********************************/
  /*******************************************************************************/

  val firstFunSig : TokenSet = TokenSet.create(
    Array (
      ScalaTokenTypes.tIDENTIFIER
    )
  )

  val firstFunDef = firstFunSig

   val firstTmplDef = TokenSet.create(
    Array(
      ScalaTokenTypes.kCASE,
      ScalaTokenTypes.kCLASS,
      ScalaTokenTypes.kOBJECT,
      ScalaTokenTypes.kTRAIT
    )
  )

  val firstDclDefKeywords = TokenSet.create(
    Array(
      ScalaTokenTypes.kVAL,
      ScalaTokenTypes.kVAR,
      ScalaTokenTypes.kDEF,
      ScalaTokenTypes.kTYPE
    )
  )

  val firstDef = TokenSet.orSet(
    Array (
      firstDclDefKeywords,
      firstTmplDef
    )
  )

  /* it uses because of when we parse TemplateStat we don't know beforehand what we parse: dcl or def. If we get '= expr'
   * this means construction is Definition, else construction is Declaration
   */

  val firstDclDef = firstDef

  val firstDcl = firstDclDefKeywords

  val firstTemplateStat = TokenSet.orSet(
    Array (
      firstImport,
      firstAttributeClause,
      firstModifier,
      firstDclDef,
      firstExpr
    )
  )

  val firstTemplateStatSeq =  TokenSet.orSet(
    Array (
      firstStatementSeparator,
      firstTemplateStat
    )
  )

  val firstConstr = firstStableId

  val firstTemplateParents = firstConstr

  val firstTemplateBody =  TokenSet.create(
    Array (
      ScalaTokenTypes.tLBRACE
    )
  )

  val firstClassTemplate = TokenSet.orSet(
    Array(
      TokenSet.create(
        Array (
          ScalaTokenTypes.kEXTENDS,
          ScalaTokenTypes.tLINE_TERMINATOR
        )
      ),
      firstTemplateBody
    )
  )


  /*******************************************************************************/
  /********************************** Top Statement ******************************/
  /*******************************************************************************/

  val firstPackaging = TokenSet.create(
    Array (
      ScalaTokenTypes.kPACKAGE
    )
  )

  val firstTopStat = TokenSet.orSet(
    Array(
      firstAttributeClause,
      firstModifier,
      firstTmplDef,
      firstImport,
      firstPackaging
    )
  )

  val firstTopStatSeq = TokenSet.orSet(
    Array (
      firstStatementSeparator,
      firstTopStat
    )
  )
}