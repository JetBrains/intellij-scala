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
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns._

  abstract class EnumTemplate(elemType: ScalaElementType,
                              assignType: ScalaElementType){

    def parse(builder : PsiBuilder) : ScalaElementType = {
      val genMarker = builder.mark()

      def badClose(st:String): ScalaElementType = {
        builder.error(st)
        genMarker.rollbackTo()
        ScalaElementTypes.WRONGWAY
      }

      def badCloseAfterAssign(st:String): ScalaElementType = {
        builder.error(st)
        genMarker.drop()
        elemType
      }

      if (ScalaTokenTypes.kVAL.equals(builder.getTokenType)){
        ParserUtils.eatElement(builder, ScalaTokenTypes.kVAL)
        ParserUtils.rollForward(builder)
        var res = Pattern1.parse(builder)
        if (ScalaElementTypes.PATTERN1.equals(res)){
          ParserUtils.rollForward(builder)
          if (assignType.equals(builder.getTokenType)){
            ParserUtils.eatElement(builder, ScalaTokenTypes.kVAL)
            ParserUtils.rollForward(builder)
            res = Expr.parse(builder)
            if (ScalaElementTypes.EXPR.equals(res)){
              ParserUtils.rollForward(builder)
              //genMarker.done(ScalaElementTypes.ENUMERATOR)
              genMarker.drop()
              elemType
            } else badCloseAfterAssign ("Wrong expression")
          } else badClose (assignType.toString + " expected")
        } else badClose ("Wrong pattern")
      } else badClose("Wrong enumerator statement")
    }


  }

  /*
  Generator ::= val Pattern1 ‘<-’ Expr
  */
  object Generator extends EnumTemplate(ScalaElementTypes.GENERATOR.asInstanceOf[ScalaElementType],
                                        ScalaTokenTypes.tCHOOSE.asInstanceOf[ScalaElementType])

  /*
  Enumerator ::=    Generator
                  | val Pattern1 ‘=’ Expr
                  | Expr
  */
  object Enumerator{
    def parse(builder : PsiBuilder) : ScalaElementType = {
      val enMarker = builder.mark()

      def genParse: Boolean = {
        var res = Generator parse builder
        ScalaElementTypes.GENERATOR.equals(res)
      }

      def enParse: Boolean = {
        var res = { object Enumer extends EnumTemplate(
                         ScalaElementTypes.ENUMERATOR.asInstanceOf[ScalaElementType],
                         ScalaTokenTypes.tASSIGN.asInstanceOf[ScalaElementType]
                         )
                     Enumer parse builder} 
        ScalaElementTypes.ENUMERATOR.equals(res)
      }

      if (genParse) {
        enMarker.done(ScalaElementTypes.ENUMERATOR)
        ScalaElementTypes.ENUMERATOR
      } else if (enParse || !ScalaElementTypes.WRONGWAY.equals(Expr.parse(builder))) {
        enMarker.done(ScalaElementTypes.ENUMERATOR)
        ScalaElementTypes.ENUMERATOR
      } else {
        enMarker.rollbackTo()
        ScalaElementTypes.WRONGWAY
      }
    }
  }


  /*
  Enumerators ::= Generator {StatementSeparator Enumerator}
  */
  /*
  object Enumerators{
    def parse(builder : PsiBuilder) : ScalaElementType = {

      val ensMarker = builder.mark()
      var res = Generator.parse(builder)



    }
  }
  */




}