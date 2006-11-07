package org.jetbrains.plugins.scala.lang.parser.parsing.patterns{
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
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template._
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._

  object SimplePattern {
  /*
  SimplePattern ::=   ‘_’
                      | varid
                      | Literal
                      | StableId [ ‘(’ [Patterns] ‘)’ ]
                      | ‘(’ [Pattern] ‘)’
                      | XmlPattern
  */

    def parse(builder : PsiBuilder) : ScalaElementType = {
        var spMarker = builder.mark()

        def parseStableId: ScalaElementType = {
          var result = StableId.parse(builder)
          if (result.equals(ScalaElementTypes.STABLE_ID)) {
            spMarker.drop()
            ScalaElementTypes.SIMPLE_PATTERN
          } else {
            spMarker.drop()
            builder.error("Wrong pattern!")
            ScalaElementTypes.SIMPLE_PATTERN
          }
        }

        //  ‘_’
        if (builder.getTokenType == ScalaTokenTypes.tUNDER) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tUNDER)
          spMarker.drop()
          ScalaElementTypes.SIMPLE_PATTERN
        // Literal
        } else if (Literal.parse(builder) == ScalaElementTypes.LITERAL) {
          spMarker.drop()
          ScalaElementTypes.SIMPLE_PATTERN
        /*  | varid
            | StableId [ ‘(’ [Patterns] ‘)’ ] */
        } else if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER){
          if (builder.getTokenText.substring(1).toLowerCase ==
              builder.getTokenText.substring(1)) {// if variable id
            ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
            if (!builder.getTokenType.equals(ScalaTokenTypes.tLPARENTHIS) &&
                !builder.getTokenType.equals(ScalaTokenTypes.tDOT)) {
              spMarker.drop()
              Console.println("varId!!!")
              ScalaElementTypes.SIMPLE_PATTERN
            } else {
              spMarker.rollbackTo()
              spMarker = builder.mark()
              parseStableId
            }
          } else { parseStableId }
        } else {
          builder.error("Wrong pattern!")
          spMarker.drop()
          ScalaElementTypes.SIMPLE_PATTERN
        }
      }
  }


  object Pattern3 {
  /*
    Pattern3 ::=   SimplePattern
                 | SimplePattern {id SimplePattern}
  */

    def parse(builder : PsiBuilder) : ScalaElementType = {
      var result = SimplePattern.parse(builder)
      if (result.equals(ScalaElementTypes.SIMPLE_PATTERN)) {
        ScalaElementTypes.PATTERN3
      } else {
        ScalaElementTypes.WRONGWAY
      }
    }
  }

  object Pattern1 {
  /*
  Pattern1 ::=    varid ‘:’ Type1
                | ‘_’ ‘:’ Type1
                | Pattern2
  */

    def parse(builder : PsiBuilder) : ScalaElementType = {
        var p1Marker = builder.mark()

        def parsePattern2 : ScalaElementType = {
          p1Marker.rollbackTo()
          p1Marker = builder.mark()
          var result = Pattern3.parse(builder)
          if (result.equals(ScalaElementTypes.PATTERN3)) {
            p1Marker.done(ScalaElementTypes.PATTERN1)
            ScalaElementTypes.PATTERN1
          }
          else  {
            p1Marker.drop()
            ScalaElementTypes.WRONGWAY
          }
        }


        //  ‘_’
        if (builder.getTokenType == ScalaTokenTypes.tUNDER) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tUNDER)
          if (builder.getTokenType == ScalaTokenTypes.tCOLON) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)
            var res = Type.parse(builder)
            if (res.equals(ScalaElementTypes.TYPE)) {
              p1Marker.done(ScalaElementTypes.PATTERN1)
              ScalaElementTypes.PATTERN1
            } else {
              builder.error("Type declaration expected")
              p1Marker.done(ScalaElementTypes.PATTERN1)
              ScalaElementTypes.PATTERN1
            }
          } else {
            parsePattern2
          }
        } else if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER &&
             (builder.getTokenText.substring(0,1).toLowerCase ==
              builder.getTokenText.substring(0,1) ) ) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
          if (builder.getTokenType == ScalaTokenTypes.tCOLON) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)
            var res = Type.parse(builder)
            if (res.equals(ScalaElementTypes.TYPE)) {
              p1Marker.done(ScalaElementTypes.PATTERN1)
              ScalaElementTypes.PATTERN1
            } else {
              p1Marker.done(ScalaElementTypes.PATTERN1)
              builder.error("Type declaration expected")
              ScalaElementTypes.PATTERN1
            }
          } else {
            parsePattern2
          }
        } else {
          parsePattern2
        }

      }
  }

  object Pattern {
  /*
    Pattern ::= Pattern1 { ‘|’ Pattern1 }
  */

    def parse(builder : PsiBuilder) : ScalaElementType = {
      val pMarker = builder.mark()

      def subParse: ScalaElementType = {
        var res = Pattern1.parse(builder)
        if (res.equals(ScalaElementTypes.PATTERN1)){
          ParserUtils.rollForward(builder)
          if (builder.getTokenType != null &&
              builder.getTokenType.equals(ScalaTokenTypes.tOR)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tOR)
            ParserUtils.rollForward(builder)
            subParse
          } else {
            pMarker.done(ScalaElementTypes.PATTERN)
            ScalaElementTypes.PATTERN
          }
        } else {
          builder error "Pattern expected"
          pMarker.done(ScalaElementTypes.PATTERN)
          ScalaElementTypes.PATTERN
        }
      }

      var result = Pattern1.parse(builder)
      if (result.equals(ScalaElementTypes.PATTERN1)) {
        ParserUtils.rollForward(builder)
        if (builder.getTokenType != null &&
            builder.getTokenType.equals(ScalaTokenTypes.tOR)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tOR)
          ParserUtils.rollForward(builder)
          subParse
        } else {
          pMarker.done(ScalaElementTypes.PATTERN)
          ScalaElementTypes.PATTERN
        }
      } else {
        pMarker.rollbackTo()
        ScalaElementTypes.WRONGWAY
      }
    }
  }




}
