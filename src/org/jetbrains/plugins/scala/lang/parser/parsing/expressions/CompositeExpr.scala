package org.jetbrains.plugins.scala.lang.parser.parsing.expressions
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
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns._

object CompositeExpr {
  /*
  Composite Expression
  Default grammar
  Expr1 ::=   if ‘(’ Expr1 ‘)’ [NewLine] Expr [[‘;’] else Expr]                           (if)
            | try ‘{’ Block ‘}’ [catch ‘{’ CaseClauses ‘}’] [finally Expr]                 (try)
            | while ‘(’ Expr ‘)’ [NewLine] Expr                                          (while)
            | do Expr [StatementSeparator] while ‘(’ Expr ’)’                            (do)
            | for (‘(’ Enumerators ‘)’ | ‘{’ Enumerators ‘}’)[NewLine] [yield] Expr        (for)
            | throw Expr                                                               (throw)
            | return [Expr]                                                            (return)
            | [SimpleExpr ‘.’] id ‘=’ Expr                                               (b2)
            | SimpleExpr ArgumentExprs ‘=’ Expr                                         (b1)
            | PostfixExpr [‘:’ CompoundType]                                                   (a)
            | PostfixExpr match ‘{’ CaseClauses ‘}’                                      (a1)
            | MethodClosure                                                            (closure)
  */

  def parse(builder: PsiBuilder): ScalaElementType = {
    val compMarker = builder.mark()

    /* Error processing */
    def errorDoneMain(rollbackMarker: PsiBuilder.Marker,
            elem: ScalaElementType) = {
      def errorDone(msg: String): ScalaElementType = {
        rollbackMarker.drop()
        //        builder.error(msg)     // Formatter bug!
        compMarker.error(msg)
        ScalaElementTypes.EXPR1
      }
      (msg: String) => errorDone(msg)
    }

    /******************************************************************/
    /*********************** Various cases ****************************/
    /******************************************************************/

    /****************************** case (a), (a1) ****************************/
    def aCase: ScalaElementType = {
      val rollbackMarker = builder.mark() // marker to rollback
      var result = PostfixExpr.parse(builder)
      if (! result.equals(ScalaElementTypes.WRONGWAY)) {
        builder getTokenType match {
          /*    [‘:’ CompoundType]   */
          case ScalaTokenTypes.tCOLON => {

            val argMarker = builder.mark()

            ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)
            val res = CompoundType parse (builder)
            res match {
              case ScalaElementTypes.COMPOUND_TYPE => {
                argMarker.drop()
                rollbackMarker.drop()
                compMarker.done(ScalaElementTypes.TYPED_EXPR_STMT)
                ScalaElementTypes.EXPR1
              }
              case _ => {
                argMarker.rollbackTo()
                rollbackMarker.drop()
                if (ScalaElementTypes.SIMPLE_EXPR.equals(result)){
                  compMarker.done(ScalaElementTypes.SIMPLE_EXPR)
                }
                else {
                  compMarker.drop
                }
                ScalaElementTypes.EXPR1
              }
            }
          }
          /* match ‘{’ CaseClauses ‘}’ */
          case ScalaTokenTypes.kMATCH => {
            ParserUtils.eatElement(builder, ScalaTokenTypes.kMATCH)
            if (builder.getTokenType.eq(ScalaTokenTypes.tLBRACE)) {
              var braceMarker = builder.mark()
              ParserUtils.eatElement(builder, ScalaTokenTypes.tLBRACE)
              var result = CaseClauses.parse(builder)
              if (ScalaElementTypes.CASE_CLAUSES.equals(result)) {
                if (builder.getTokenType.eq(ScalaTokenTypes.tRBRACE)){
                  ParserUtils.eatElement(builder, ScalaTokenTypes.tRBRACE)
                  braceMarker.done(ScalaElementTypes.BLOCK_EXPR)
                  rollbackMarker.drop()
                  compMarker.done(ScalaElementTypes.MATCH_STMT)
                  ScalaElementTypes.EXPR1
                } else {
                  braceMarker.drop()
                  builder.error("Case clauses expected")
                  rollbackMarker.drop()
                  ParserUtils.rollPanicToBrace(builder, ScalaTokenTypes.tLBRACE, ScalaTokenTypes.tRBRACE)
                  compMarker.done(ScalaElementTypes.MATCH_STMT)
                  ScalaElementTypes.EXPR1
                }
              } else {
                braceMarker.drop()
                builder.error("Case clauses expected")
                rollbackMarker.drop()
                ParserUtils.rollPanicToBrace(builder, ScalaTokenTypes.tLBRACE, ScalaTokenTypes.tRBRACE)
                compMarker.done(ScalaElementTypes.MATCH_STMT)
                ScalaElementTypes.EXPR1
              }
            } else {
              rollbackMarker.rollbackTo()
              ScalaElementTypes.WRONGWAY
            }
          }
          case _ => {
            if (! ScalaElementTypes.INFIX_EXPR.equals(result) &&
            ! ScalaElementTypes.POSTFIX_EXPR.equals(result) &&
            ! ScalaElementTypes.PREFIX_EXPR.equals(result)){
              if (ScalaTokenTypes.tASSIGN.equals(builder.getTokenType) ||
              ScalaTokenTypes.tLPARENTHIS.equals(builder.getTokenType) ||
              ScalaTokenTypes.tLBRACE.equals(builder.getTokenType)) {
                rollbackMarker.rollbackTo()
                ScalaElementTypes.WRONGWAY
              }
              else {
                rollbackMarker.drop()
                //compMarker.done(result)
                compMarker.drop
                ScalaElementTypes.EXPR1
              }
            }
            else {
              rollbackMarker.drop()
              compMarker.drop
              ScalaElementTypes.EXPR1
            }
          }
        }
      } else {
        rollbackMarker.rollbackTo()
        ScalaElementTypes.WRONGWAY
      }
    }

    /*********************** cases (b1), (b2) **************************/
    def bCase: ScalaElementType = {

      var rollbackMarker = builder.mark() // marker to rollback

      def assignProcess: ScalaElementType = {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tASSIGN)
        var res = Expr.parse(builder)
        if (res.eq(ScalaElementTypes.EXPR)) {
          rollbackMarker.drop()
          compMarker.done(ScalaElementTypes.ASSIGN_STMT)
          ScalaElementTypes.EXPR1
        } else {
          rollbackMarker.drop()
          compMarker.error("Expression expected")
          ScalaElementTypes.EXPR1
        }
      }

      def processSimpleExpr: ScalaElementType = {
        var res = SimpleExpr.parse(builder, null, false)
        if (! res.parsed.equals(ScalaElementTypes.WRONGWAY) &&
        (res.endness.eq("argexprs") || res.endness.eq(".id"))) {
          if (builder.getTokenType.eq(ScalaTokenTypes.tASSIGN)) {
            assignProcess
          } else {
            rollbackMarker.rollbackTo()
            ScalaElementTypes.WRONGWAY
          }
        } else {
          rollbackMarker.rollbackTo()
          ScalaElementTypes.WRONGWAY
        }
      }

      if (builder.getTokenType.eq(ScalaTokenTypes.tIDENTIFIER)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
        if (builder.getTokenType.eq(ScalaTokenTypes.tASSIGN)) {
          assignProcess
        } else {
          rollbackMarker.rollbackTo()
          rollbackMarker = builder.mark()
          processSimpleExpr
        }
      } else if (builder.getTokenType.eq(ScalaTokenTypes.kTHIS) ||
      builder.getTokenType.eq(ScalaTokenTypes.kSUPER)){
        processSimpleExpr
      } else {
        processSimpleExpr
      }
    }


    /*
      id = Expr
    */
    def b1Case: ScalaElementType = {

      var rollbackMarker = builder.mark() // marker to rollback

      def assignProcess: ScalaElementType = {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tASSIGN)
        var res = Expr.parse(builder)
        if (res.eq(ScalaElementTypes.EXPR)) {
          rollbackMarker.drop()
          compMarker.done(ScalaElementTypes.ASSIGN_STMT)
          ScalaElementTypes.EXPR1
        } else {
          rollbackMarker.drop()
          compMarker.error("Expression expected")
          ScalaElementTypes.EXPR1
        }
      }

      if (builder.getTokenType.eq(ScalaTokenTypes.tIDENTIFIER)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
        if (builder.getTokenType.eq(ScalaTokenTypes.tASSIGN)) {
          assignProcess
        } else {
          rollbackMarker.rollbackTo()
          ScalaElementTypes.WRONGWAY
        }
      } else {
        rollbackMarker.drop()
        ScalaElementTypes.WRONGWAY
      }
    }

    /******************************* case (if) ****************************/
    def ifCase: ScalaElementType = {
      val rollbackMarker = builder.mark() // marker to rollback

      /* for mistakes processing */
      def errorDone = errorDoneMain(rollbackMarker, ScalaElementTypes.IF_STMT)

      def elseProcessing: ScalaElementType = {

        val rMarker = builder.mark()
        if (builder.getTokenType.equals(ScalaTokenTypes.tSEMICOLON) ||
        builder.getTokenType.equals(ScalaTokenTypes.tLINE_TERMINATOR)){
          ParserUtils.eatElement(builder, builder.getTokenType)
        }
        if (builder.getTokenType.eq(ScalaTokenTypes.kELSE)) {
          rMarker.drop()
          ParserUtils.eatElement(builder, ScalaTokenTypes.kELSE)
          val res2 = Expr.parse(builder)
          if (res2.eq(ScalaElementTypes.EXPR)){
            rollbackMarker.drop()
            compMarker.done(ScalaElementTypes.IF_STMT)
            ScalaElementTypes.EXPR1
          } else errorDone("Wrong expression")
        } else {
          rMarker.rollbackTo()
          rollbackMarker.drop()
          compMarker.done(ScalaElementTypes.IF_STMT)
          ScalaElementTypes.EXPR1
        }
      }

      /* Parse body of IF statement */
      def parseContent = {
        // Warning!
        ParserUtils.rollForward(builder)
        val res1 = Expr.parse(builder)
        if (res1.eq(ScalaElementTypes.EXPR)){
          var mileMarker = builder.mark()

          /* else? */
          var tempMarker = builder.mark()
          if (! builder.eof) builder.advanceLexer()
          var second = builder.getTokenType
          tempMarker.rollbackTo()

          builder.getTokenType match {
            case ScalaTokenTypes.kELSE
              | ScalaTokenTypes.tSEMICOLON => {
              mileMarker.drop()
              elseProcessing
            }
            case ScalaTokenTypes.tLINE_TERMINATOR if (ScalaTokenTypes.kELSE.equals(second)) => {
              mileMarker.drop()
              elseProcessing
            }
            case _ => {
              mileMarker.rollbackTo()
              rollbackMarker.drop()
              compMarker.done(ScalaElementTypes.IF_STMT)
              ScalaElementTypes.EXPR1
            }
          }
        } else errorDone("Wrong expression")
      }

      if (builder.getTokenType.eq(ScalaTokenTypes.kIF)){
        ParserUtils.eatElement(builder, ScalaTokenTypes.kIF)
        if (ScalaTokenTypes.tLPARENTHIS.equals(builder.getTokenType)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLPARENTHIS)
          val res = Expr parse (builder)
          if (res.eq(ScalaElementTypes.EXPR)){
            if (builder.getTokenType.eq(ScalaTokenTypes.tRPARENTHIS)){
              ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHIS)
              parseContent
            } else {
              builder.error(" ) expected")
              ParserUtils.rollPanicToBrace(builder, ScalaTokenTypes.tLPARENTHIS, ScalaTokenTypes.tRPARENTHIS)
              if (! builder.eof) {
                parseContent
              }
              else {
                rollbackMarker.drop()
                compMarker.done(ScalaElementTypes.IF_STMT)
                ScalaElementTypes.EXPR1
              }
            }
          } else {
            builder.error("Wrong expression")
            ParserUtils.rollPanicToBrace(builder, ScalaTokenTypes.tLPARENTHIS, ScalaTokenTypes.tRPARENTHIS)
            if (! builder.eof) {
              parseContent
            }
            else {
              rollbackMarker.drop()
              compMarker.done(ScalaElementTypes.IF_STMT)
              ScalaElementTypes.EXPR1
            }
          }
        } else errorDone("( expected")
      } else {
        rollbackMarker.rollbackTo()
        ScalaElementTypes.WRONGWAY
      }
    }


    /***************************** case (while) *************************/
    def whileCase: ScalaElementType = {
      val rollbackMarker = builder.mark() // marker to rollback

      /* WHILE statement body parsing */
      def parseContent = {
        // Warning!
        ParserUtils.rollForward(builder)
        val res1 = Expr.parse(builder)
        if (res1.eq(ScalaElementTypes.EXPR)){
          rollbackMarker.drop()
          compMarker.done(ScalaElementTypes.WHILE_STMT)
          ScalaElementTypes.EXPR1
        } else {
          errorDone("Wrong expression")
        }
      }

      def parseError(st: String) = {
        //        builder.error(st)
        ParserUtils.rollPanicToBrace(builder, ScalaTokenTypes.tLPARENTHIS, ScalaTokenTypes.tRPARENTHIS)
        if (! builder.eof) {
          parseContent
        }
        else {
          rollbackMarker.drop()
          compMarker.error(st)
          ScalaElementTypes.EXPR1
        }
      }

      /* for mistakes processing */
      def errorDone = errorDoneMain(rollbackMarker, ScalaElementTypes.WHILE_STMT)

      if (builder.getTokenType.eq(ScalaTokenTypes.kWHILE)){
        ParserUtils.eatElement(builder, ScalaTokenTypes.kWHILE)
        if (ScalaTokenTypes.tLPARENTHIS.equals(builder.getTokenType)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLPARENTHIS)
          val res = Expr parse (builder)
          if (ScalaElementTypes.EXPR.eq(res)){
            if (ScalaTokenTypes.tRPARENTHIS.equals(builder.getTokenType)){
              ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHIS)
              parseContent
            } else parseError(") expected")
          } else parseError("Wrong expression")
        } else errorDone("( expected")
      } else {
        rollbackMarker.rollbackTo()
        ScalaElementTypes.WRONGWAY
      }
    }

    /***************************** case (do) *************************/

    def doCase: ScalaElementType = {
      val rollbackMarker = builder.mark() // marker to rollback

      /* for mistakes processing */
      def errorDone = errorDoneMain(rollbackMarker, ScalaElementTypes.DO_STMT)

      def whileProcessing = {
        if (builder.getTokenType.eq(ScalaTokenTypes.kWHILE)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.kWHILE)
          if (builder.getTokenType.eq(ScalaTokenTypes.tLPARENTHIS)){
            ParserUtils.eatElement(builder, ScalaTokenTypes.tLPARENTHIS)
            val res = Expr parse (builder)
            if (res.eq(ScalaElementTypes.EXPR)){
              if (builder.getTokenType.eq(ScalaTokenTypes.tRPARENTHIS)){
                ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHIS)
                rollbackMarker.drop()
                compMarker.done(ScalaElementTypes.DO_STMT)
                ScalaElementTypes.EXPR1
              } else errorDone(" ) expected")
            } else errorDone("Wrong expression")
          } else errorDone("( expected")
        } else errorDone("while expected")
      }

      if (builder.getTokenType.eq(ScalaTokenTypes.kDO)){
        ParserUtils.eatElement(builder, ScalaTokenTypes.kDO)
        val res1 = Expr.parse(builder)
        if (res1.eq(ScalaElementTypes.EXPR)){
          builder.getTokenType match {
            case ScalaTokenTypes.tLINE_TERMINATOR
              | ScalaTokenTypes.tSEMICOLON => ParserUtils.eatElement(builder, builder.getTokenType)
            case _ =>
          }
          whileProcessing
        } else errorDone("Wrong expression")
      } else {
        rollbackMarker.rollbackTo()
        ScalaElementTypes.WRONGWAY
      }
    }

    /******************** case (for) ************************/

    def forCase: ScalaElementType = {
      val rollbackMarker = builder.mark() // marker to rollback

      /* for mistakes processing */
      def errorDone = errorDoneMain(rollbackMarker, ScalaElementTypes.FOR_STMT)

      def bodyParse = {
        ParserUtils.rollForward(builder)
        if (ScalaTokenTypes.kYIELD.equals(builder.getTokenType)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.kYIELD)
        }
        val res1 = Expr.parse(builder)
        if (res1.eq(ScalaElementTypes.EXPR)){
          rollbackMarker.drop()
          compMarker.done(ScalaElementTypes.FOR_STMT)
          ScalaElementTypes.EXPR1
        } else errorDone("Wrong expression")
      }

      def parseError(st: String, elem1: IElementType, elem2: IElementType) = {
        builder.error(st)
        ParserUtils.rollPanicToBrace(builder, elem1, elem2)
        if (! builder.eof) {
          bodyParse
        }
        else {
          rollbackMarker.drop()
          compMarker.done(ScalaElementTypes.FOR_STMT)
          ScalaElementTypes.EXPR1
        }
      }

      def braceMatcher(brace: IElementType) = {
        val rightBrace = brace match {
          case ScalaTokenTypes.tLBRACE => ScalaTokenTypes.tRBRACE.asInstanceOf[ScalaElementType]
          case _ => ScalaTokenTypes.tRPARENTHIS.asInstanceOf[ScalaElementType]
        }
        ParserUtils.eatElement(builder, brace)
        val res = Enumerators.parse(builder, rightBrace)
        if (res.eq(ScalaElementTypes.ENUMERATORS)){
          if (rightBrace.equals(builder.getTokenType)){
            ParserUtils.eatElement(builder, rightBrace)
            bodyParse
          } else parseError(rightBrace.toString + " expected", brace, rightBrace)
        } else parseError("Wrong enumerators", brace, rightBrace)
      }

      if (builder.getTokenType.eq(ScalaTokenTypes.kFOR)){
        ParserUtils.eatElement(builder, ScalaTokenTypes.kFOR)
        if (builder.getTokenType.eq(ScalaTokenTypes.tLPARENTHIS) ||
        builder.getTokenType.eq(ScalaTokenTypes.tLBRACE)) {
          braceMatcher(builder.getTokenType)
        } else errorDone("( of { expected")
      } else {
        rollbackMarker.rollbackTo()
        ScalaElementTypes.WRONGWAY
      }
    }

    /******************** case (try) ************************/

    def tryCase: ScalaElementType = {
      val rollbackMarker = builder.mark() // marker to rollback

      /* for mistakes processing */
      def errorDone = errorDoneMain(rollbackMarker, ScalaElementTypes.FOR_STMT)

      def parseCatch = {
        val catcMarker = builder.mark()
        ParserUtils.eatElement(builder, ScalaTokenTypes.kCATCH)
        if (builder.getTokenType.equals(ScalaTokenTypes.tLBRACE)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLBRACE)
          /* ‘{’ CaseClauses ‘}’ */
          var result = CaseClauses.parse(builder)
          if (result.equals(ScalaElementTypes.CASE_CLAUSES)) {
            if (builder.getTokenType.eq(ScalaTokenTypes.tRBRACE)){
              ParserUtils.eatElement(builder, ScalaTokenTypes.tRBRACE)
            } else {
              builder.error("} expected")
              ParserUtils.rollPanicToBrace(builder, ScalaTokenTypes.tLBRACE, ScalaTokenTypes.tRBRACE)
            }
          } else {
            builder.error("Wrong case clauses")
            ParserUtils.rollPanicToBrace(builder, ScalaTokenTypes.tLBRACE, ScalaTokenTypes.tRBRACE)
          }
        } else {
          builder.error(" { expected ")
        }
        catcMarker.done(ScalaElementTypes.CATCH_BLOCK)
        ScalaElementTypes.CATCH_BLOCK
      }

      def parseFinally = {
        var finMarker = builder.mark()
        ParserUtils.eatElement(builder, ScalaTokenTypes.kFINALLY)
        var result = Expr.parse(builder)
        if (result.equals(ScalaElementTypes.WRONGWAY)) {
          builder.error("Wrong expression")
        }
        finMarker.done(ScalaElementTypes.FINALLY_BLOCK)
        ScalaElementTypes.FINALLY_BLOCK
      }

      def braceMatcher(brace: IElementType, tryMarker: PsiBuilder.Marker) = {
        val rightBrace = brace match {
          case ScalaTokenTypes.tLBRACE => ScalaTokenTypes.tRBRACE.asInstanceOf[ScalaElementType]
          case _ => ScalaTokenTypes.tRPARENTHIS.asInstanceOf[ScalaElementType]
        }
        ParserUtils.eatElement(builder, brace)
        val res = Block.parse(builder, true)
        if (res.eq(ScalaElementTypes.BLOCK)){
          if (builder.getTokenType.equals(rightBrace)){
            ParserUtils.eatElement(builder, rightBrace)
            tryMarker.done(ScalaElementTypes.TRY_BLOCK)
            if (ScalaTokenTypes.kCATCH.equals(builder.getTokenType)) parseCatch
            if (ScalaTokenTypes.kFINALLY.equals(builder.getTokenType)) parseFinally
          } else {
            builder.error(brace + " expected")
            ParserUtils.rollPanicToBrace(builder, ScalaTokenTypes.tLBRACE, ScalaTokenTypes.tRBRACE)
            tryMarker.done(ScalaElementTypes.TRY_BLOCK)
          }
        } else {
          builder.error("Wrong block")
          ParserUtils.rollPanicToBrace(builder, ScalaTokenTypes.tLBRACE, ScalaTokenTypes.tRBRACE)
          tryMarker.done(ScalaElementTypes.TRY_BLOCK)
        }
        compMarker.done(ScalaElementTypes.TRY_STMT)
        ScalaElementTypes.EXPR1
      }

      if (builder.getTokenType.eq(ScalaTokenTypes.kTRY)){
        val tryMarker = builder.mark()
        ParserUtils.eatElement(builder, ScalaTokenTypes.kTRY)
        rollbackMarker.drop()
        if (ScalaTokenTypes.tLBRACE.equals(builder.getTokenType)) {
          braceMatcher(ScalaTokenTypes.tLBRACE, tryMarker)
        } else {
          builder.error(" { expected")
          tryMarker.done(ScalaElementTypes.TRY_BLOCK)
          compMarker.done(ScalaElementTypes.TRY_STMT)
          ScalaElementTypes.EXPR1
        }
      } else {
        rollbackMarker.rollbackTo()
        ScalaElementTypes.WRONGWAY
      }
    }

    /*********************** case (closure) ********************/
    def closureCase: ScalaElementType = {
      val rollbackMarker = builder.mark() // marker to rollback

      def close = {
        rollbackMarker.drop()
        compMarker.done(ScalaElementTypes.METHOD_CLOSURE)
        ScalaElementTypes.EXPR1
      }

      /* for mistakes processing */
      def errorDone = errorDoneMain(rollbackMarker, ScalaElementTypes.METHOD_CLOSURE)

      def subParse: ScalaElementType =
        builder.getTokenType match {
          case ScalaTokenTypes.tDOT => {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tDOT)
            if (builder.getTokenType.eq(ScalaTokenTypes.tIDENTIFIER)){
              ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
              subParse
            } else errorDone("Identifier expected")
          }
          case ScalaTokenTypes.tLBRACE
            | ScalaTokenTypes.tLPARENTHIS => {
            ArgumentExprs parse builder
            subParse
          }
          case ScalaTokenTypes.tLSQBRACKET  => {
            TypeArgs parse builder
            subParse
          }
          case _ => close
        }

      if (builder.getTokenType.eq(ScalaTokenTypes.tDOT)){
        ParserUtils.eatElement(builder, ScalaTokenTypes.tDOT)
        if (builder.getTokenType.eq(ScalaTokenTypes.tIDENTIFIER)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
          subParse
        } else errorDone("Identifier expected")
      } else {
        rollbackMarker.rollbackTo()
        ScalaElementTypes.WRONGWAY
      }
    }


    /*********************** case (throw) **********************/

    def throwCase: ScalaElementType = {
      val rollbackMarker = builder.mark() // marker to rollback

      /* for mistakes processing */
      def errorDone = errorDoneMain(rollbackMarker, ScalaElementTypes.THROW_STMT)
      if (builder.getTokenType.eq(ScalaTokenTypes.kTHROW)){
        ParserUtils.eatElement(builder, ScalaTokenTypes.kTHROW)
        val res = Expr parse (builder)
        if (res.eq(ScalaElementTypes.EXPR)){
          rollbackMarker.drop()
          compMarker.done(ScalaElementTypes.THROW_STMT)
          ScalaElementTypes.EXPR1
        } else errorDone("Wrong expression")
      } else {
        rollbackMarker.rollbackTo()
        ScalaElementTypes.WRONGWAY
      }
    }

    /********************** case (return) **************************/
    def returnCase: ScalaElementType = {
      val rollbackMarker = builder.mark() // marker to rollback
      /* for mistakes processing */
      def errorDone = errorDoneMain(rollbackMarker, ScalaElementTypes.RETURN_STMT)
      if (builder.getTokenType.eq(ScalaTokenTypes.kRETURN)){
        ParserUtils.eatElement(builder, ScalaTokenTypes.kRETURN)
        val res = Expr parse (builder)
        rollbackMarker.drop()
        compMarker.done(ScalaElementTypes.RETURN_STMT)
        ScalaElementTypes.EXPR1
      } else {
        rollbackMarker.rollbackTo()
        ScalaElementTypes.WRONGWAY
      }
    }

    var result = ScalaElementTypes.WRONGWAY

    /* Various variants of parsing */
    def variants(variantProcessing: => ScalaElementType): Boolean = {
      result = variantProcessing
      ! result.equals(ScalaElementTypes.WRONGWAY)
    }

    def variants1 = {
      result = builder.getTokenType match {
        case ScalaTokenTypes.kIF => ifCase
        case ScalaTokenTypes.kTRY => tryCase
        case ScalaTokenTypes.kFOR => forCase
        case ScalaTokenTypes.kRETURN => returnCase
        case ScalaTokenTypes.kTHROW => throwCase
        case ScalaTokenTypes.kWHILE => whileCase
        case ScalaTokenTypes.kDO => doCase
        case ScalaTokenTypes.tDOT => closureCase
        case _ => ScalaElementTypes.WRONGWAY
      }
    }

    variants1
    if (ScalaElementTypes.WRONGWAY.equals(result)) {
      if (variants(b1Case)) result
      /* case (a) */
      else if (variants(aCase)) result
      /* cases (b1), (b2) */
      else if (variants(bCase)) result
      else {
        compMarker.rollbackTo()
        ScalaElementTypes.WRONGWAY
      }
    } else result

    /*
      /* Parsing function body */
      /* case (throw) */
      if (variants(throwCase)) result
      /* case (return) */
      else if (variants(returnCase)) result
      /* case (if) */
      else if (variants(ifCase)) result
      /* case (try) */
      else if (variants(tryCase)) result
      /* case (for) */
      else if (variants(forCase)) result
      /* case (closure) */
      else if (variants(closureCase)) result
      /* case (while) */
      else if (variants(whileCase)) result
      /* case (do) */
      else if (variants(doCase)) result
      /* special b case */
      else if (variants(b1Case)) result
      /* case (a) */
      else if (variants(aCase)) result
      /* cases (b1), (b2) */
      else if (variants(bCase)) result
      else {
        compMarker.rollbackTo()
        ScalaElementTypes.WRONGWAY
      }
    */

  }

}