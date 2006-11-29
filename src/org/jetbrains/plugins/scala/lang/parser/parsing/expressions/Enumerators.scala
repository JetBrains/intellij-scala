import scala.collection.mutable._

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
        genMarker.done(elemType)
        elemType
      }

      if (ScalaTokenTypes.kVAL.equals(builder.getTokenType)){
        ParserUtils.eatElement(builder, ScalaTokenTypes.kVAL)
        var res = Pattern1.parse(builder)
        if (ScalaElementTypes.PATTERN1.equals(res)){
          if (assignType.equals(builder.getTokenType)){
            ParserUtils.eatElement(builder, ScalaTokenTypes.kVAL)
            res = Expr.parse(builder)
            if (ScalaElementTypes.EXPR.equals(res)){
              if (ScalaElementTypes.GENERATOR.equals(elemType)){
                genMarker.done(ScalaElementTypes.ENUMERATOR)
              } else {
                genMarker.drop()
              }
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
        var res = { object Enumer extends EnumTemplate(
                         ScalaElementTypes.ENUMERATOR.asInstanceOf[ScalaElementType],
                         ScalaTokenTypes.tCHOOSE.asInstanceOf[ScalaElementType]
                         )
                     Enumer parse builder}
        ScalaElementTypes.ENUMERATOR.equals(res)
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
      } else if (enParse ){
        enMarker.done(ScalaElementTypes.ENUMERATOR)
        ScalaElementTypes.ENUMERATOR
      } else if (!ScalaElementTypes.WRONGWAY.equals(Expr.parse(builder))) {
        enMarker.done(ScalaElementTypes.ENUMERATOR)
        ScalaElementTypes.ENUMERATOR
      } else {
        builder.error("Wrong enumerator")
        enMarker.done(ScalaElementTypes.ENUMERATOR)
        ScalaElementTypes.WRONGWAY
      }
    }
  }

  /*
  Enumerators ::= Generator {StatementSeparator Enumerator}
  */

  object Enumerators{

    val elems = new HashSet[IElementType]

    def parse(builder : PsiBuilder) : ScalaElementType = {

      val ensMarker = builder.mark()
      elems += ScalaTokenTypes.tLINE_TERMINATOR
      elems += ScalaTokenTypes.tSEMICOLON
      //elems += ScalaTokenTypes.tRPARENTHIS

      def matchToken = builder.getTokenType match {
          case  ScalaTokenTypes.tSEMICOLON
              | ScalaTokenTypes.tLINE_TERMINATOR => {
            ParserUtils.eatElement(builder, builder.getTokenType)
            subParse
          }
          case _ => {
            ensMarker.done(ScalaElementTypes.ENUMERATORS)
            ScalaElementTypes.ENUMERATORS
          }
        }

      def subParse: ScalaElementType = {
        if (!ScalaTokenTypes.tRPARENTHIS.equals(builder.getTokenType)){
          var res = Enumerator.parse(builder)
          if (ScalaElementTypes.WRONGWAY.equals(res)){
            builder.error("Enumerator expected")
            ParserUtils.rollPanic(builder, elems)
          } else {
            if (!elems.contains(builder.getTokenType)
               && !ScalaTokenTypes.tRPARENTHIS.equals(builder.getTokenType) ) {
              builder.error("Wrong enumerator")
              ParserUtils.rollPanic(builder, elems)
            }
            else {}
          }
          matchToken
        } else {
          builder.error("Enumerator expected")
          ensMarker.done(ScalaElementTypes.ENUMERATORS)
          ScalaElementTypes.ENUMERATORS
        }
      }

      var res = Generator.parse(builder)
      if (ScalaElementTypes.WRONGWAY.equals(res)){
        builder.error("Wrong generator")
        ensMarker.done(ScalaElementTypes.ENUMERATORS)
        ScalaElementTypes.ENUMERATORS
      } else {
        matchToken
      }

    }
  }





}