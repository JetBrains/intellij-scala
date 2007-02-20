package org.jetbrains.plugins.scala.lang.parser.bnf

import com.intellij.psi.tree.TokenSet, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.psi.tree.IElementType, org.jetbrains.plugins.scala.lang.parser._

object BNF {

  val literals: Array[IElementType] =
    Array.apply(ScalaTokenTypes.tINTEGER,
            ScalaTokenTypes.tFLOAT,
            ScalaTokenTypes.kTRUE,
            ScalaTokenTypes.kFALSE,
            ScalaTokenTypes.tCHAR,
            ScalaTokenTypes.kNULL)


  val tLITERALS: TokenSet = TokenSet.create(literals)

  val tSIMPLE_FIRST: TokenSet = TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER,
          ScalaTokenTypes.kTHIS,
          ScalaTokenTypes.kSUPER,
          ScalaTokenTypes.tLPARENTHIS,
          ScalaTokenTypes.kNEW))

  val tPREFIXES: TokenSet = TokenSet.create(Array(ScalaTokenTypes.tPLUS,
          ScalaTokenTypes.tMINUS,
          ScalaTokenTypes.tTILDA,
          ScalaTokenTypes.tNOT))

  /*********** LAST **************/
  var lastTemplateStat = TokenSet.create(Array(ScalaTokenTypes.tRBRACE))



  /********************************************************/
  /*********************** FIRSTS *************************/

  val firstStableId: TokenSet = TokenSet.create(Array(ScalaTokenTypes.kTHIS,
          ScalaTokenTypes.kSUPER,
          ScalaTokenTypes.tIDENTIFIER))

  //todo
  val firstPattern2: TokenSet = TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER,
          ScalaTokenTypes.tUNDER,
          ScalaElementTypes.LITERAL,
          ScalaTokenTypes.tLPARENTHIS,
          ScalaTokenTypes.tLBRACE))

  val firstExpr: TokenSet = TokenSet.create(Array(ScalaTokenTypes.tINTEGER,
          ScalaTokenTypes.tFLOAT,
          ScalaTokenTypes.tCHAR,
          ScalaTokenTypes.kNULL,
          ScalaTokenTypes.tSTRING,

          ScalaTokenTypes.tDOT,
          ScalaTokenTypes.kTRUE,
          ScalaTokenTypes.kTHIS,
          ScalaTokenTypes.kSUPER,
          ScalaTokenTypes.kFALSE,
          ScalaTokenTypes.kIF,
          ScalaTokenTypes.kWHILE,
          ScalaTokenTypes.kDO,
          ScalaTokenTypes.kTRY,
          ScalaTokenTypes.kFOR,
          ScalaTokenTypes.kTHROW,
          ScalaTokenTypes.kRETURN,
          ScalaTokenTypes.tPLUS,
          ScalaTokenTypes.tMINUS,
          ScalaTokenTypes.tTILDA,
          ScalaTokenTypes.tSTAR,
          ScalaTokenTypes.tNOT,
          ScalaTokenTypes.tIDENTIFIER,
          ScalaTokenTypes.tLBRACE,
          ScalaTokenTypes.tLPARENTHIS,
          ScalaTokenTypes.kNEW,
          //todo: check
          ScalaElementTypes.LITERAL))


  val firstArgumentExprs: TokenSet = TokenSet.create(Array(ScalaTokenTypes.tLBRACE,
          ScalaTokenTypes.tLPARENTHIS))


  val firstStatementSeparator: TokenSet = TokenSet.create(Array(ScalaTokenTypes.tLINE_TERMINATOR,
          ScalaTokenTypes.tSEMICOLON))

  //todo: add first(Type)
  val firstType: TokenSet = TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER,
          ScalaTokenTypes.tLPARENTHIS,
          ScalaTokenTypes.kSUPER,
          ScalaTokenTypes.kTHIS,
          ScalaTokenTypes.tLBRACE))

  //todo
  val firstSimpleType: TokenSet = TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER,
          ScalaTokenTypes.tLPARENTHIS))

  /********************************************************************************************/
  /********************************* Import, Attribute and Modifier ***************************/

  val firstImport = TokenSet.create(Array(ScalaTokenTypes.kIMPORT))

  val firstImportSelector = TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER))

  val firstLocalModifier = TokenSet.create(Array(ScalaTokenTypes.kABSTRACT,
          ScalaTokenTypes.kFINAL,
          ScalaTokenTypes.kIMPLICIT,
          ScalaTokenTypes.kSEALED))

  val firstLocalModifierWithoutImplicit = TokenSet.create(Array(ScalaTokenTypes.kABSTRACT,
          ScalaTokenTypes.kFINAL,
          ScalaTokenTypes.kSEALED))



  val firstAccessModifier: TokenSet = TokenSet.create(Array(ScalaTokenTypes.kPRIVATE,
          ScalaTokenTypes.kPROTECTED))


  val firstModifierWithoutImplicit: TokenSet = TokenSet.orSet(Array(TokenSet.create(Array(ScalaTokenTypes.kOVERRIDE)),
          firstAccessModifier,
          firstLocalModifierWithoutImplicit))

  val firstModifier: TokenSet = TokenSet.orSet(Array(TokenSet.create(Array(ScalaTokenTypes.kOVERRIDE)),
          firstAccessModifier,
          firstLocalModifier))

  val firstAttributeClause: TokenSet = TokenSet.create(Array(ScalaTokenTypes.tLSQBRACKET))


  /************* parameters ***************/

  val firstTypeParam = TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER))

  val firstVariantTypeParam = TokenSet.orSet(Array(TokenSet.create(Array(ScalaTokenTypes.tPLUS,
          ScalaTokenTypes.tMINUS)),
          firstTypeParam))

  val firstFunTypeParam = TokenSet.create(Array(ScalaTokenTypes.tLINE_TERMINATOR,
          ScalaTokenTypes.tLSQBRACKET))

  val firstTypeParamClause = TokenSet.create(Array(ScalaTokenTypes.tLINE_TERMINATOR,
          ScalaTokenTypes.tLSQBRACKET))

  val firstClassTypeParamClause = TokenSet.create(Array(ScalaTokenTypes.tLINE_TERMINATOR,
          ScalaTokenTypes.tLSQBRACKET))


  val firstParamClause = TokenSet.create(Array(ScalaTokenTypes.tLINE_TERMINATOR,
          ScalaTokenTypes.tLPARENTHIS))

  val firstClassParamClause = TokenSet.create(Array(ScalaTokenTypes.tLINE_TERMINATOR,
          ScalaTokenTypes.tLPARENTHIS))


  val firstParamClauses = TokenSet.orSet(Array(firstParamClause,
          TokenSet.create(Array(ScalaTokenTypes.tLPARENTHIS))))


  val firstClassParamClauses = TokenSet.orSet(Array(firstClassParamClause,
          TokenSet.create(Array(ScalaTokenTypes.tLPARENTHIS))))


  val firstParamType = TokenSet.orSet(Array(firstType,
          TokenSet.create(Array(ScalaTokenTypes.tFUNTYPE))))

  val firstParam = TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER))

  val firstClassParam = TokenSet.orSet(Array(firstModifierWithoutImplicit,
          TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER,
                  ScalaTokenTypes.kVAR,
                  ScalaTokenTypes.kVAL)),
          firstParam))

  /*******************************************************************************/
  /********************************** Def and Dcl ********************************/

  val firstFunSig: TokenSet = TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER))


  val firstSelfInvocation: TokenSet = TokenSet.create(Array(ScalaTokenTypes.kTHIS))

  val firstConstrExpr: TokenSet = TokenSet.orSet(Array(firstSelfInvocation,
          TokenSet.create(Array(ScalaTokenTypes.tLBRACE))))

  val firstFunDef = firstFunSig

  val firstTmplDef = TokenSet.create(Array(ScalaTokenTypes.kCASE,
          ScalaTokenTypes.kCLASS,
          ScalaTokenTypes.kOBJECT,
          ScalaTokenTypes.kTRAIT))

  val firstDclDefKeywords = TokenSet.create(Array(ScalaTokenTypes.kVAL,
          ScalaTokenTypes.kVAR,
          ScalaTokenTypes.kDEF,
          ScalaTokenTypes.kTYPE))

  //todo: expr -> expr1
  val firstBlockStat: TokenSet = TokenSet.orSet(Array(firstImport,
          firstLocalModifier,
          firstTmplDef,
          firstExpr,
          firstDclDefKeywords))

  val firstBindings = TokenSet.create(Array(ScalaTokenTypes.tLBRACE))

  //todo: expr -> expr1
  val firstResultExpr: TokenSet = TokenSet.orSet(Array(firstBindings,
          firstExpr,
          TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER))))

  val firstBlock: TokenSet = TokenSet.orSet(Array(firstBlockStat,
          firstResultExpr))

  val firstDef = TokenSet.orSet(Array(firstDclDefKeywords,
          firstTmplDef))

  val firstTypeDef = TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER))

  /* it uses because of when we parse TemplateStat we don't know beforehand what we parse: dcl or def. If we get '= expr'
   * this means construction is Definition, else construction is Declaration
   */

  val firstDclDef = firstDef

  val firstDcl = firstDclDefKeywords

  val firstTemplateStat = TokenSet.orSet(Array(firstImport,
          firstAttributeClause,
          firstModifier,
          firstDclDef,
          firstExpr))                      

  val firstTemplateStatSeq =  TokenSet.orSet(Array(firstStatementSeparator,
          firstTemplateStat))

  val firstConstr = firstStableId

  val firstTemplateParents = firstConstr

  val firstTemplateBody =  TokenSet.create(Array(ScalaTokenTypes.tLBRACE))

  val firstClassTemplate = TokenSet.orSet(Array(TokenSet.create(Array(ScalaTokenTypes.kEXTENDS,
          ScalaTokenTypes.tLINE_TERMINATOR)),
          firstTemplateBody))

  val firstTraitTemplate = firstClassTemplate

  val firstMixinParents = firstSimpleType

  /*******************************************************************************/
  /********************************** Top Statement ******************************/
  /*******************************************************************************/

  val firstPackaging = TokenSet.create(Array(ScalaTokenTypes.kPACKAGE))

  val firstTopStat = TokenSet.orSet(Array(firstAttributeClause,
          firstModifier,
          firstTmplDef,
          firstImport,
          firstPackaging))

  val firstTopStatSeq = TokenSet.orSet(Array(firstStatementSeparator,
          firstTopStat))
}