package org.jetbrains.plugins.scala.lang.parser.parsing.types {
  /**
  Parsing various types with its names and declarations
  */

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType

  /*
  Class for StableId representation and parsing
  */
  object StableId{

  /*
  STABLE ID
  Default grammar:

  StableId ::= id
              | StableId ‘.’ id
              | [id ‘.’] this ‘.’ id
              | [id ’.’] super [‘[’ id ‘]’] ‘.’ id ‘.’ id

  *******************************************

  FIRST(StableId) = ScalaTokenTypes.tIIDENTIFIER
  */
    /** Parses StableId
    * @return ScalaElementTypes.STABLE_ID if really StableId parsed,
    * ScalaElementTypes.PATH if ONLY Path parsed,
    * ScalaElementTypes.WRONGWAY else
    */
    def parse(builder : PsiBuilder) : ScalaElementType = {

      /** processing [‘[’ id ‘]’] statement**/
      def parseGeneric(currentMarker: PsiBuilder.Marker): Boolean = {
        ParserUtils.eatElement(builder, ScalaElementTypes.LSQBRACKET)
        if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaElementTypes.IDENTIFIER)
          if (ScalaTokenTypes.tRSQBRACKET.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaElementTypes.RSQBRACKET)
            true
          } else false
        } else false
      }

      /** "super" keyword processing **/
      def afterSuper(currentMarker: PsiBuilder.Marker): ScalaElementType = {
        val nextMarker = currentMarker.precede()
        currentMarker.drop()
        ParserUtils.eatElement(builder, ScalaElementTypes.DOT)
        if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaElementTypes.IDENTIFIER)
          if (ScalaTokenTypes.tDOT.equals(builder.getTokenType)) {
            val nextMarker1 = nextMarker.precede()
            nextMarker.drop()
            ParserUtils.eatElement(builder, ScalaElementTypes.DOT)
              if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
                ParserUtils.eatElement(builder, ScalaElementTypes.IDENTIFIER)
                Console.println("token type : " + builder.getTokenType())
                builder.getTokenType() match {
                  case ScalaTokenTypes.tDOT => {
                    val nextMarker2 = nextMarker1.precede()
                    nextMarker1.done(ScalaElementTypes.STABLE_ID)
                    ParserUtils.eatElement(builder, ScalaElementTypes.DOT)
                    leftRecursion(nextMarker2)
                  }
                  case _ => {
                    nextMarker1.done(ScalaElementTypes.STABLE_ID)
                    ScalaElementTypes.STABLE_ID
                  }
                }
              } else ParserUtils.errorToken(builder, nextMarker1, "Wrong id declaration", ScalaElementTypes.STABLE_ID)
          } else {
            nextMarker.done(ScalaElementTypes.PATH)
            ScalaElementTypes.PATH
          }
        } else ParserUtils.errorToken(builder, nextMarker, "Wrong id declaration", ScalaElementTypes.STABLE_ID)
      }

      /** sequence like  {'.' id } processing **/
      def leftRecursion (currentMarker: PsiBuilder.Marker): ScalaElementType = {
        builder.getTokenType match {
          case ScalaTokenTypes.tIDENTIFIER => {
            ParserUtils.eatElement(builder, ScalaElementTypes.IDENTIFIER)
            builder.getTokenType match {
              case ScalaTokenTypes.tDOT => {
                val nextMarker = currentMarker.precede()
                currentMarker.done(ScalaElementTypes.STABLE_ID)
                ParserUtils.eatElement(builder, ScalaElementTypes.DOT)
                leftRecursion(nextMarker)
              }
              case _ => {
                currentMarker.done(ScalaElementTypes.STABLE_ID)
                ScalaElementTypes.STABLE_ID
              }
            }
          }
          case _ => ParserUtils.errorToken(builder, currentMarker, "Wrong id declaration", ScalaElementTypes.STABLE_ID)
        }
      }

      def afterDotParse(currentMarker: PsiBuilder.Marker): ScalaElementType = {
        builder.getTokenType match {
          /************** THIS ***************/
          case ScalaTokenTypes.kTHIS => {
            ParserUtils.eatElement(builder, ScalaElementTypes.THIS)
            if (ScalaTokenTypes.tDOT.equals(builder.getTokenType)){
              val newMarker = currentMarker.precede()
              //currentMarker.done(ScalaElementTypes.STABLE_ID)
              currentMarker.drop()
              ParserUtils.eatElement(builder, ScalaElementTypes.DOT)
              if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)){
                ParserUtils.eatElement(builder, ScalaElementTypes.IDENTIFIER)
                builder.getTokenType match {
                  case ScalaTokenTypes.tDOT => {
                    val nextMarker = newMarker.precede()
                    newMarker.done(ScalaElementTypes.STABLE_ID)
                    ParserUtils.eatElement(builder, ScalaElementTypes.DOT)
                    leftRecursion(nextMarker)
                  }
                  case _ => {
                    newMarker.done(ScalaElementTypes.STABLE_ID)
                    ScalaElementTypes.STABLE_ID
                  }
                }
              } else ParserUtils.errorToken(builder, newMarker, "Wrong id declaration", ScalaElementTypes.STABLE_ID)
            } else { 
              currentMarker.done(ScalaElementTypes.PATH)
              ScalaElementTypes.PATH
            }
          }
          /***************** SUPER ****************/
          case ScalaTokenTypes.kSUPER => {
            ParserUtils.eatElement(builder, ScalaElementTypes.SUPER)
            var res = true
            if (ScalaTokenTypes.tLSQBRACKET.equals(builder.getTokenType)) {
              res = parseGeneric(currentMarker)
            }
            if (res && ScalaTokenTypes.tDOT.equals(builder.getTokenType)) {
              afterSuper(currentMarker)
            } else ParserUtils.errorToken(builder, currentMarker, "Wrong id declaration", ScalaElementTypes.STABLE_ID)
          }
          /***************** IDENTIFIER ****************/
          case ScalaTokenTypes.tIDENTIFIER => {
            ParserUtils.eatElement(builder, ScalaElementTypes.IDENTIFIER)
            builder.getTokenType match {
              case ScalaTokenTypes.tDOT => {
                val nextMarker = currentMarker.precede()
                currentMarker.done(ScalaElementTypes.STABLE_ID)
                ParserUtils.eatElement(builder, ScalaElementTypes.DOT)
                leftRecursion(nextMarker)
              }
              case _ => {
                currentMarker.done(ScalaElementTypes.STABLE_ID)
                ScalaElementTypes.STABLE_ID
              }
            }
          }
          /******************* OTHER *********************/
          case _ => ParserUtils.errorToken(builder, currentMarker, "Wrong id declaration", ScalaElementTypes.STABLE_ID)
        }
      }

      def stableIdSubParse(currentMarker: PsiBuilder.Marker) : ScalaElementType = {
        builder.getTokenType match {
          case ScalaTokenTypes.tIDENTIFIER => {
            ParserUtils.eatElement(builder, ScalaElementTypes.IDENTIFIER)
            builder.getTokenType match {
              case ScalaTokenTypes.tDOT => {
                val nextMarker = currentMarker.precede()
                currentMarker.done(ScalaElementTypes.STABLE_ID)
                ParserUtils.eatElement(builder, ScalaElementTypes.DOT)
                afterDotParse(nextMarker)
              }
              case _ => {
                currentMarker.done(ScalaElementTypes.STABLE_ID)
                ScalaElementTypes.STABLE_ID
              }
            }
          }
          case ScalaTokenTypes.kTHIS | ScalaTokenTypes.kSUPER => {
            afterDotParse(currentMarker)
          }
          case _ => ParserUtils.errorToken(builder, currentMarker, "Wrong id declaration", ScalaElementTypes.STABLE_ID)
        }
      }

      val stableMarker = builder.mark()
      var result = stableIdSubParse(stableMarker)
      result
    }
  }

  object Path {

  /*
  PATH
  Default grammar:
  Path ::= StableId
          | [id ‘.’] this
          | [id ’.’] super [‘[’ id ‘]’]‘.’ id
  *******************************************
  */

    /** Parses Path
    * @return ScalaElementTypes.PATH if Path or StableId parsed,
    * ScalaElementTypes.WRONGWAY else
    */
    def parse(builder : PsiBuilder) : ScalaElementType = {
      var result = StableId.parse(builder)
      if (result.equals(ScalaElementTypes.STABLE_ID))
        ScalaElementTypes.PATH
      else result
    }
  }

  object SimpleType {

  /*
  SimpleType
  Default grammar:
  SimpleType ::= SimpleType TypeArgs
            | SimpleType ‘#’ id
            | StableId
            | Path ‘.’ type
            | ‘(’ Type ’)’
  *******************************************
  */

    /** Parses Simple Type
    * @return ScalaElementTypes.SimpleType if Simple Type,
    * ScalaElementTypes.WRONGWAY else
    */
    def parse(builder : PsiBuilder) : ScalaElementType = {
      val simpleMarker = builder.mark()
      // If it is plain Stable Id
      var result = StableId.parse(builder)
      if (result.equals(ScalaElementTypes.STABLE_ID)){
        simpleMarker.done(ScalaElementTypes.SIMPLE_TYPE)
        ScalaElementTypes.SIMPLE_TYPE
      } else if (result.equals(ScalaElementTypes.PATH)) {
          
      }

      ScalaElementTypes.WRONGWAY
    }
  }


}