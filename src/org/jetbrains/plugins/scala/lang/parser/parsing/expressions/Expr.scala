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

  import com.intellij.psi.PsiFile
  import com.intellij.lang.ParserDefinition

  import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
  import org.jetbrains.plugins.scala.lang.parser.parsing.types._
  import org.jetbrains.plugins.scala.ScalaFileType
  import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
  import org.jetbrains.plugins.scala.util.DebugPrint
  import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
  import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

  import com.intellij.openapi.util.TextRange

  import com.intellij.lang.ASTNode
  import com.intellij.psi.impl.source.tree.CompositeElement
  import com.intellij.util.CharTable
  import com.intellij.lexer.Lexer
  import com.intellij.lang.impl.PsiBuilderImpl
  //import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
  import com.intellij.psi._
  import com.intellij.psi.impl.source.CharTableImpl

  object ResultExpr {
    /*
    Result expression
    Default grammar
    Expr ::= ( Bindings | Id ) => Expr
            | Expr1               (a)
    */

    def parse(builder: PsiBuilder): ScalaElementType = {
      var exprMarker = builder.mark()
      var result = Bindings.parse(builder)

      //Console.println(result)

      def parseComposite: ScalaElementType = {
        result = CompositeExpr.parse(builder)
        if (! ScalaElementTypes.WRONGWAY.equals(result)) {
          //exprMarker.done(ScalaElementTypes.EXPR1)
          exprMarker.drop
          ScalaElementTypes.EXPR1
        } else {
          exprMarker.rollbackTo()
          ScalaElementTypes.RESULT_EXPR          }
      }

      def parseTail: ScalaElementType = {

        if (ScalaTokenTypes.tRBRACE.equals(builder.getTokenType)){
          exprMarker.done(ScalaElementTypes.RESULT_EXPR)
          ScalaElementTypes.RESULT_EXPR
        } else {
          var res = Block.parse(builder, false)

          if (! ScalaElementTypes.WRONGWAY.equals(res)) {
            exprMarker.done(ScalaElementTypes.RESULT_EXPR)
            ScalaElementTypes.RESULT_EXPR
          } else {
            exprMarker.rollbackTo()
            ScalaElementTypes.RESULT_EXPR
          }
        }
      }

      val rbMarker = builder.mark()
      var first = builder.getTokenType;
      builder.advanceLexer;
      var second = builder.getTokenType;
      builder.advanceLexer;
      var third = builder.getTokenType;
      rbMarker.rollbackTo()

      if (ScalaTokenTypes.tLPARENTHESIS.equals(first) &&
      ScalaTokenTypes.tRPARENTHESIS.equals(second) &&
      ! ScalaTokenTypes.tDOT.equals(third)) {

        // () => ...
        /* Let's kick it! */
        val uMarker = builder.mark()
        builder.getTokenType
        ParserUtils.eatElement(builder, ScalaTokenTypes.tLPARENTHESIS)
        builder.getTokenType
        ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHESIS)
        uMarker.done(ScalaElementTypes.UNIT)
        if (ScalaTokenTypes.tFUNTYPE.equals(builder.getTokenType)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.tFUNTYPE)
          parseTail
        } else {
          exprMarker.done(ScalaElementTypes.RESULT_EXPR)
          ScalaElementTypes.RESULT_EXPR
        }
      } else if (ScalaTokenTypes.tIDENTIFIER.equals(first) &&
      ScalaTokenTypes.tFUNTYPE.equals(second)) {
        /* Let's kick it! */
        builder.getTokenType
        ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
        builder.getTokenType
        ParserUtils.eatElement(builder, ScalaTokenTypes.tFUNTYPE)
        parseTail
      } else if (ScalaTokenTypes.tIDENTIFIER.equals(first) &&
      ScalaTokenTypes.tCOLON.equals(second)){
        //var rbMarker = builder.mark()
        builder.getTokenType
        ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
        builder.getTokenType
        ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)
        var res3 = CompoundType parse builder
        if (ScalaElementTypes.COMPOUND_TYPE.equals(res3)) {
          if (ScalaTokenTypes.tFUNTYPE.equals(builder.getTokenType)){
            ParserUtils.eatElement(builder, ScalaTokenTypes.tFUNTYPE)
            parseTail
          } else {
            builder.error("=> expected")
            exprMarker.done(ScalaElementTypes.RESULT_EXPR)
            ScalaElementTypes.RESULT_EXPR
          }
        } else {
          builder.error("Wrong type")
          exprMarker.done(ScalaElementTypes.RESULT_EXPR)
          ScalaElementTypes.RESULT_EXPR
        }
      } else if (ScalaElementTypes.BINDINGS.equals(result)){
        ParserUtils.rollForward(builder)
        builder.getTokenType match {
          case ScalaTokenTypes.tFUNTYPE => {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tFUNTYPE)
            parseTail
          }
          case _ => {
            exprMarker.rollbackTo()
            exprMarker = builder.mark()
            parseComposite
          }
        }
      } else {
        parseComposite
      }
    }
  }

  object Expr {
    /*
    Common expression
    Default grammar
    Expr ::= ( Bindings | Id ) => Expr
            | Expr1               (a)
    */

    private val DUMMY = "dummy.";
/*
    def createExpressionFromText(buffer: String, manager: PsiManager): ASTNode = {
      def isExpr = (elementType: IElementType) => (ScalaElementTypes.EXPRESSION_BIT_SET.contains(elementType))

      val definition: ParserDefinition = ScalaFileType.SCALA_FILE_TYPE.getLanguage.getParserDefinition
      //    if (definition != null) ...
      val text = "class a {" + buffer + "}"

      val facade = JavaPsiFacade.getInstance(manager.getProject)
      val dummyFile: PsiFile = facade.getElementFactory().createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), text)

      val classDef = dummyFile.getFirstChild
      val topDefTmpl = classDef.getLastChild
      val templateBody = topDefTmpl.getFirstChild.asInstanceOf[ScalaPsiElementImpl]

      val expression = templateBody.childSatisfyPredicateForElementType(isExpr)

      if (expression == null) return null

      expression.asInstanceOf[ScalaExpression].getNode

      //    val expression : ScExprImpl = dummyFile.getFirstChild.getLastChild.asInstanceOf[ScalaPsiElementImpl].childSatisfyPredicate(isExpr).asInstanceOf[ScExprImpl]
    }
*/

    def parse(builder: PsiBuilder): ScalaElementType = {
      var exprMarker = builder.mark()
      var result = ScalaElementTypes.WRONGWAY

      def parseComposite: ScalaElementType = {
        result = CompositeExpr.parse(builder)
        if (ScalaElementTypes.EXPR1.equals(result)) {
          exprMarker.drop()
          ScalaElementTypes.EXPR
        } else {
          exprMarker.rollbackTo()
          ScalaElementTypes.WRONGWAY
        }
      }

      def parseTail: ScalaElementType = {
        var res = parse(builder)
        if (! ScalaElementTypes.WRONGWAY.equals(res)) {
          exprMarker.done(ScalaElementTypes.AN_FUN)
          ScalaElementTypes.EXPR
        } else {
          builder.error("Expression expected")
          exprMarker.done(ScalaElementTypes.AN_FUN)
          ScalaElementTypes.EXPR
        }
      }

      val rbMarker = builder.mark()

      var first = if (builder.getTokenType != null) builder.getTokenType
      else ScalaTokenTypes.tWRONG
      ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
      var second = if (builder.getTokenType != null) builder.getTokenType
      else ScalaTokenTypes.tWRONG

      val rb = builder.mark()
      builder.advanceLexer
      var third = if (builder.getTokenType != null) builder.getTokenType
      else ScalaTokenTypes.tWRONG
      rb.rollbackTo()
      ParserUtils.eatElement(builder, ScalaTokenTypes.tFUNTYPE)


      if (ScalaTokenTypes.tLPARENTHESIS.equals(first) &&
      ScalaTokenTypes.tRPARENTHESIS.equals(second) &&
      ! ScalaTokenTypes.tDOT.equals(third)) {  // () => ...
        /* Let's kick it! */
        rbMarker.rollbackTo()
        val uMarker = builder.mark()
        builder.getTokenType
        ParserUtils.eatElement(builder, ScalaTokenTypes.tLPARENTHESIS)
        builder.getTokenType
        ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHESIS)
        uMarker.done(ScalaElementTypes.UNIT)
        if (ScalaTokenTypes.tFUNTYPE.equals(builder.getTokenType)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.tFUNTYPE)
          parseTail
        } else {
          exprMarker.drop
          ScalaElementTypes.EXPR
        }
      } else if (ScalaTokenTypes.tIDENTIFIER.equals(first) &&
      ScalaTokenTypes.tFUNTYPE.equals(second)) {
        rbMarker.drop()
        parseTail
      } else if ({
        rbMarker.rollbackTo()
        result = Bindings.parse(builder)
        ScalaElementTypes.BINDINGS.equals(result)
      }){
        builder.getTokenType match {
          case ScalaTokenTypes.tFUNTYPE => {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tFUNTYPE)
            ParserUtils.rollForward(builder)
            parseTail
          }
          case _ => {
            exprMarker.rollbackTo()
            exprMarker = builder.mark()
            parseComposite
          }
        }
      } else {
        parseComposite
      }
    }
  }

  object Exprs {
    /*
    Expression list
    Default grammar
    Exprs ::= Expr {, Expr}
    */

    def parse(builder: PsiBuilder, marker: PsiBuilder.Marker): ScalaElementType = {

      val exprsMarker = marker match {
        case null => builder.mark()
        case marker => marker
      }

      def subParse: ScalaElementType = {
        //ParserUtils.rollForward(builder)
        builder getTokenType match {
          case ScalaTokenTypes.tCOMMA => {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tCOMMA)
            ParserUtils.rollForward(builder)
            val res1 = Expr.parse(builder)
            if (res1.equals(ScalaElementTypes.EXPR)) {
              subParse
            } else {
              builder.error("Argument expected")
              //exprsMarker.done(ScalaElementTypes.EXPRS)
              exprsMarker.drop
              ScalaElementTypes.EXPRS
            }
          }
          case ScalaTokenTypes.tCOLON => {
            ParserUtils.eatElement(builder, builder.getTokenType)
            val rbm = builder.mark()
            if (ScalaTokenTypes.tUNDER.equals(builder.getTokenType) && // _ ...
            {
              ParserUtils.eatElement(builder, builder.getTokenType)
              "*".equals(builder.getTokenText)                   // _* ...
            } &&
            {
              ParserUtils.eatElement(builder, builder.getTokenType)
              ScalaTokenTypes.tRPARENTHESIS.equals(builder.getTokenType)
            }) {
              rbm.drop()
              exprsMarker.drop
              ScalaElementTypes.EXPRS
            } else {
              rbm.rollbackTo()
              builder.error("Sequence type expected!")
              exprsMarker.drop
              ScalaElementTypes.EXPRS
            }
          }
          case _ => {
            //exprsMarker.done(ScalaElementTypes.EXPRS)
            exprsMarker.drop
            ScalaElementTypes.EXPRS
          }
        }
      }

      if (marker == null) {
        var result = Expr.parse(builder)
        /** Case (a) **/
        if (result.equals(ScalaElementTypes.EXPR)) {
          subParse
        }
        else {
          builder.error("Argument expected")
          exprsMarker.drop
          ScalaElementTypes.EXPRS
        }
      } else {
        subParse
      }
    }
  }



}