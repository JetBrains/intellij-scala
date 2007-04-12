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

  object Bindings {
  /*
      Bindings ::= ‘(’ Binding {‘,’ Binding} ‘)’
      Binding ::= id [‘:’ Type]
  */

    def parse(builder : PsiBuilder) : ScalaElementType = {
        var bindMarker = builder.mark()

        /* Parses one binding */
        def oneBindingParse: ScalaElementType = {

          var oneBindMarker = builder.mark()
          if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)){
            val vm = builder.mark()
            ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
            vm.done(ScalaElementTypes.REFERENCE)
            if (ScalaTokenTypes.tCOLON.equals(builder.getTokenType)){
              ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)
              var res = Type.parse(builder)
              if (res.equals(ScalaElementTypes.TYPE)) {
                oneBindMarker.done(ScalaElementTypes.BINDING)
                ScalaElementTypes.BINDING
              } else {
                oneBindMarker.rollbackTo()
                ScalaElementTypes.WRONGWAY
              }
            } else {
              oneBindMarker.done(ScalaElementTypes.BINDING)
              ScalaElementTypes.BINDING
            }
          } else {
            oneBindMarker.rollbackTo()
            ScalaElementTypes.WRONGWAY
          }
        }

        def subParse: ScalaElementType = {
          var res = oneBindingParse
          if (res.equals(ScalaElementTypes.BINDING)){
            builder.getTokenType match {
              case ScalaTokenTypes.tRPARENTHESIS => {
                ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHESIS)
                bindMarker.drop()
                ScalaElementTypes.BINDINGS
              }
              case ScalaTokenTypes.tCOMMA => {
                ParserUtils.eatElement(builder, ScalaTokenTypes.tCOMMA)
                subParse
              }
              case _ => {
                builder.error("Wrong symbol: ',' of ')' expected")
                bindMarker.drop()
                ScalaElementTypes.BINDINGS
              }
            }
          } else {
            builder.error("binding expected")
            bindMarker.drop()
            ScalaElementTypes.BINDINGS

          }
        }

        if (ScalaTokenTypes.tLPARENTHESIS.equals(builder.getTokenType)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLPARENTHESIS)
          var res = oneBindingParse
          if (res.equals(ScalaElementTypes.BINDING)){
            builder.getTokenType match {
              case ScalaTokenTypes.tRPARENTHESIS => {
                ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHESIS)
                bindMarker.drop()
                ScalaElementTypes.BINDINGS
              }
              case ScalaTokenTypes.tCOMMA => {
                ParserUtils.eatElement(builder, ScalaTokenTypes.tCOMMA)
                subParse
              }
              case _ => {
                bindMarker.rollbackTo()
                ScalaElementTypes.WRONGWAY
              }
            }
          } else {
            bindMarker.rollbackTo()
            ScalaElementTypes.WRONGWAY
          }
        } else {
          bindMarker.rollbackTo()
          ScalaElementTypes.WRONGWAY
        }
      }

  }



}