package org.jetbrains.plugins.scala.lang.parser.parsing.top.params {

  /**
  * User: Dmitry.Krasilschikov
  * Date: 02.11.2006
  * Time: 12:43:03
  */
  import com.intellij.psi.tree.IElementType
  import com.intellij.psi.tree.TokenSet
  import com.intellij.lang.PsiBuilder

  import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
  import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
  import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
  import org.jetbrains.plugins.scala.lang.parser.parsing.Constr
  import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
  import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
  import org.jetbrains.plugins.scala.util.DebugPrint
  import org.jetbrains.plugins.scala.lang.parser.parsing.types.ParamType

  /*
  *  ParamClauses ::= {ParamClause} ImplicitEnd
  */

  class ParamClauses[T <: Param](param: T) extends ConstrUnpredict {
    //      override def getElementType = ScalaElementTypes.PARAM_CLAUSES

    def checkForParamClause[T <: Param](param: T, first: IElementType, second: IElementType, third: IElementType): Boolean = {
      if (first == null || second == null || third == null) return false

      var a = first
      var b = second
      var c = third
      if (a.equals(ScalaTokenTypes.tLINE_TERMINATOR)) {
        a = b
        b = c
      }

      a.equals(ScalaTokenTypes.tLPARENTHESIS) && (param.first.contains(b) || b.equals(ScalaTokenTypes.tRPARENTHESIS))

    }

    def checkForImplicit(first: IElementType, second: IElementType, third: IElementType): Boolean = {
      if (first == null || second == null || third == null) return false

      var a = first
      var b = second
      var c = third
      if (a.equals(ScalaTokenTypes.tLINE_TERMINATOR)) {
        a = b
        b = c
      }

      a.equals(ScalaTokenTypes.tLPARENTHESIS) && b.equals(ScalaTokenTypes.kIMPLICIT)

    }


    override def parseBody(builder: PsiBuilder): Unit = {
      var paramClausesMarker = builder.mark
      var chooseParsingWay = builder.mark

      var first = builder.getTokenType()
      builder.advanceLexer

      var second = builder.getTokenType()
      builder.advanceLexer

      var third = builder.getTokenType()

      var numberParamClauses = 0;

      //it is possible to cyclic
      while (checkForParamClause[T](param, first, second, third)) {
        chooseParsingWay.rollbackTo()
        new ParamClause[T](param).parse(builder)
        numberParamClauses = numberParamClauses + 1
        chooseParsingWay = builder.mark()

        first = builder.getTokenType()
        builder.advanceLexer
        second = builder.getTokenType()
        builder.advanceLexer
        third = builder.getTokenType()
      }

      chooseParsingWay.rollbackTo()

      if (checkForImplicit(first, second, third)) {
        new ImplicitEnd[T](param).parse(builder)
      }

      if (numberParamClauses > 1) paramClausesMarker.done(ScalaElementTypes.PARAM_CLAUSES)
      else paramClausesMarker.drop()
    }
  }

  /*
  *  ParamClause ::= [NewLine] { ( [Params] ) }
  */

  class ParamClause[T <: Param](param: T) extends Constr {
    override def getElementType: IElementType = ScalaElementTypes.PARAM_CLAUSE

    override def parseBody(builder: PsiBuilder): Unit = {
      if (ScalaTokenTypes.tLINE_TERMINATOR.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tLINE_TERMINATOR)
      }

      if (ScalaTokenTypes.tLPARENTHESIS.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tLPARENTHESIS)

      } else {
        builder error "'(' expected"
        return
      }

      if (param.first.contains(builder.getTokenType)){
        ParserUtils.listOfSmth(builder, param, ScalaTokenTypes.tCOMMA, ScalaElementTypes.PARAMS)
      }

      if (ScalaTokenTypes.tRPARENTHESIS.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHESIS)
      } else {
        builder error "')' expected"
        return
      }
    }
  }

  /*
  *  ImplicitEnd = [NewLine] '(' 'implicit'  ClassParams ')'
  */

  class ImplicitEnd[T <: Param](param: T) extends Constr{
    override def getElementType: IElementType = ScalaElementTypes.PARAM_CLAUSE

    override def parseBody(builder: PsiBuilder): Unit = {
      if (ScalaTokenTypes.tLINE_TERMINATOR.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tLINE_TERMINATOR)
      }

      if (ScalaTokenTypes.tLPARENTHESIS.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tLPARENTHESIS)
      } else {
        builder.error("'(' expected")
        return
      }

      if (ScalaTokenTypes.kIMPLICIT.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.kIMPLICIT)
      } else {
        builder.error("'implicit' expected")
        return
      }

      if (param.first.contains(builder.getTokenType())) {
        ParserUtils.listOfSmth(builder, param, ScalaTokenTypes.tCOMMA, ScalaElementTypes.PARAM_CLAUSE)
      } else {
        builder.error("parameter or list of parameters expected")
        return
      }

      if (ScalaTokenTypes.tRPARENTHESIS.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHESIS)
      } else {
        builder.error("')' expected")
        return
      }
    }
  }

  /*
  *  Param ::= id : ParamType
  */

  class Param extends ConstrItem {
    override def getElementType: IElementType = ScalaElementTypes.PARAM

    override def first: TokenSet = BNF.firstParam

    override def parseBody(builder: PsiBuilder): Unit = {
      if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
        val marker = builder.mark()
        ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
        marker.done(ScalaElementTypes.REFERENCE)
      } else {
        builder error "identifier expected"
        return
      }

      if (ScalaTokenTypes.tCOLON.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)
      } else {
        builder error "':' expected"
      }

      if (BNF.firstParamType.contains(builder.getTokenType)){
        ParamType parse builder
      } else {
        builder error "type parameter expected"
      }
    }
  }

  /*
  *  ParamType ::= [=>] Type [*]
  */

  /*object ParamType extends ConstrUnpredict {
    override def parseBody(builder: PsiBuilder): Unit = {
      var paramTypeMarker = builder.mark
      var isParamType = false;

      if (ScalaTokenTypes.tFUNTYPE.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tFUNTYPE)
        isParamType = true;
      }

      if (BNF.firstType.contains(builder.getTokenType)) {
        Type parse builder
        isParamType = false;
      } else {
        builder error "type declaration expected"
      }

      if ("*".equals(builder.getTokenText)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tSTAR)
        isParamType = true;
      }

      if (isParamType) paramTypeMarker.done(ScalaElementTypes.PARAM_TYPE)
      else {
        //paramTypeMarker.done(ScalaElementTypes.TYPE)
        paramTypeMarker.drop()
      }
    }
  }*/
}