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

  //object StableId extends Constr{
  object StableId {

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

//*******************************************************
//please, rewrite it so, that parse(builder : PsiBuilder return Unit)
//for example, make method parseStableId and use it, as need//and so, we 'll extend StableId by Constr
//*******************************************************

    def parse(builder : PsiBuilder) : ScalaElementType = {

      /**
      * Process keyword "type" encountering 
      */
      def typeProcessing( dotMarker: PsiBuilder.Marker,
                          nextMarker: PsiBuilder.Marker,
                          doneOrDrop: Boolean,
                          elem: ScalaElementType,
                          processFunction: PsiBuilder.Marker => ScalaElementType,
                          doWithMarker: Boolean
                        ): ScalaElementType = {
        if (ScalaTokenTypes.kTYPE.equals(builder.getTokenType)){
          dotMarker.rollbackTo()
          if (doneOrDrop) nextMarker.done(elem)
          else nextMarker.drop()
          elem
        } else {
        if (doWithMarker) dotMarker.done(ScalaElementTypes.DOT)
        else dotMarker.drop()
        processFunction(nextMarker)
        }
      }

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
            // If keyWord type encountered
            val dotMarker = builder.mark()
            builder.advanceLexer // Ate DOT
              if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
                dotMarker.done(ScalaElementTypes.DOT)
                ParserUtils.eatElement(builder, ScalaElementTypes.IDENTIFIER)
                Console.println("token type : " + builder.getTokenType())
                builder.getTokenType() match {
                  case ScalaTokenTypes.tDOT => {
                    val nextMarker2 = nextMarker1.precede()
                    nextMarker1.done(ScalaElementTypes.STABLE_ID)
                    val dotMarker1 = builder.mark()
                    builder.advanceLexer // Ate DOT
                    typeProcessing(dotMarker1, nextMarker2, false, ScalaElementTypes.STABLE_ID, leftRecursion, true)
                  }
                  case _ => {
                    nextMarker1.done(ScalaElementTypes.STABLE_ID)
                    ScalaElementTypes.STABLE_ID
                  }
                }
              } else {
                typeProcessing(dotMarker,
                               nextMarker1,
                               true,
                               ScalaElementTypes.PATH,
                               (marker1: PsiBuilder.Marker) => ParserUtils.errorToken(builder,
                                                                                    marker1,
                                                                                    "Wrong id declaration",
                                                                                    ScalaElementTypes.STABLE_ID),
                               false)
              }
          } else {
            nextMarker.done(ScalaElementTypes.PATH)
            ScalaElementTypes.PATH
          }
        } else ParserUtils.errorToken(builder, nextMarker, "Wrong id declaration", ScalaElementTypes.PATH)
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
                val dotMarker = builder.mark()
                builder.advanceLexer //Ate DOT
                typeProcessing(dotMarker, nextMarker, false, ScalaElementTypes.STABLE_ID, leftRecursion, true)
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
              val dotMarker = builder.mark()
              builder.advanceLexer // Ate DOT
              if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)){
                val newMarker = currentMarker.precede()
                currentMarker.drop()
                dotMarker.done(ScalaElementTypes.DOT)
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
              } else{
                typeProcessing(dotMarker,
                               currentMarker,
                               true,
                               ScalaElementTypes.PATH,
                               (marker1: PsiBuilder.Marker) => ParserUtils.errorToken(builder,
                                                                                    marker1,
                                                                                    "Wrong id declaration",
                                                                                    ScalaElementTypes.STABLE_ID),
                               false)
              }
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
                val dotMarker = builder.mark()
                builder.advanceLexer //Ate DOT
                typeProcessing(dotMarker, nextMarker, false, ScalaElementTypes.STABLE_ID, leftRecursion, true)
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
                val dotMarker = builder.mark()
                builder.advanceLexer //Ate DOT
                typeProcessing(dotMarker, nextMarker, false, ScalaElementTypes.STABLE_ID, afterDotParse, true)
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

      /*
      Parsing alternatives:
      SimpleType ::= StableId
                    | Path ‘.’ type
      */
      def simpleTypeSubParse(currentMarker : PsiBuilder.Marker) : ScalaElementType = {
        var result = StableId.parse(builder)

        if (!result.equals(ScalaElementTypes.WRONGWAY)){
          builder.getTokenType match {
            case ScalaTokenTypes.tDOT => {
              ParserUtils.eatElement(builder, ScalaElementTypes.DOT)
              builder.getTokenType match {
                case ScalaTokenTypes.kTYPE => {
                  ParserUtils.eatElement(builder, ScalaElementTypes.KEY_TYPE)
                  //currentMarker.done(ScalaElementTypes.SIMPLE_TYPE)
                  ScalaElementTypes.SIMPLE_TYPE
                }
                case _ => ParserUtils.errorToken(builder, currentMarker, "Wrong type", ScalaElementTypes.SIMPLE_TYPE)
              }
            }
            case _ => {
              if (result.equals(ScalaElementTypes.STABLE_ID)){
                //currentMarker.done(ScalaElementTypes.SIMPLE_TYPE)
                ScalaElementTypes.SIMPLE_TYPE
              } else ParserUtils.errorToken(builder, currentMarker, "Wrong type", ScalaElementTypes.SIMPLE_TYPE)
            }
          }
        } else ParserUtils.errorToken(builder, currentMarker, "Wrong type", ScalaElementTypes.SIMPLE_TYPE)
      }

      def leftRecursion(currentMarker : PsiBuilder.Marker) : ScalaElementType = {
        builder.getTokenType match {
          case ScalaTokenTypes.tINNER_CLASS => {
            val nextMarker = currentMarker.precede()
            currentMarker.done(ScalaElementTypes.SIMPLE_TYPE)
            ParserUtils.eatElement(builder, ScalaElementTypes.INNER_CLASS)
            builder.getTokenType match {
              case ScalaTokenTypes.tIDENTIFIER => {
                ParserUtils.eatElement(builder, ScalaElementTypes.IDENTIFIER)
                leftRecursion(nextMarker)
              }
              case _ => ParserUtils.errorToken(builder, nextMarker, "Wrong type", ScalaElementTypes.SIMPLE_TYPE)
            }
          }
          case _ => {
            currentMarker.done(ScalaElementTypes.SIMPLE_TYPE)
            ScalaElementTypes.SIMPLE_TYPE
          }
        }
      }

      val simpleMarker = builder.mark()
      var res = simpleTypeSubParse(simpleMarker)
      if (!res.equals(ScalaElementTypes.WRONGWAY)){
        leftRecursion(simpleMarker)
      } else res 
    }

  }

  object Type1 {

  /*
  Type1
  Default grammar:
  Type1 ::= SimpleType {with SimpleType} [Refinement]
  *******************************************
  */

    def parse(builder : PsiBuilder) : ScalaElementType = {

      def subParse : ScalaElementType = {
        var result = SimpleType parse(builder)
        result match {
          case ScalaElementTypes.SIMPLE_TYPE => {
            builder.getTokenType match {
              case ScalaTokenTypes.kWITH => {
                ParserUtils.eatElement(builder, ScalaElementTypes.WITH)
                subParse
              }
              case _ => {
                ScalaElementTypes.TYPE1
              }
            }
          }
          case _ => result
        }
      }

      //val type1Marker = builder.mark()
      var res = subParse
      //type1Marker.done(ScalaElementTypes.TYPE1)
      res
    }

  }


  object Type {

  /*
  Type
  Default grammar:
  Type ::= Type1 ‘=>’ Type
           | ‘(’ [Types] ‘)’ ‘=>’ Type
           | Type1
  *******************************************
  */
    def parse(builder : PsiBuilder) : ScalaElementType = {

      // If ')' symbol - the end of list of parameter list encountered
      def rightBraceProcessing : ScalaElementType = {
        ParserUtils.eatElement(builder, ScalaElementTypes.RPARENTHIS)
        if (ScalaTokenTypes.tFUNTYPE.equals(builder.getTokenType)){
          ParserUtils.eatElement(builder, ScalaElementTypes.FUN_TYPE)
          parse(builder)
        } else {
          builder.error(" => expected")
          ScalaElementTypes.WRONGWAY
        }
      }

      def subParse : ScalaElementType = {
        if (!ScalaTokenTypes.tLPARENTHIS.equals(builder.getTokenType)){
          var result = Type1 parse(builder)
          result match {
            case ScalaElementTypes.TYPE1 => {
              builder.getTokenType match {
                case ScalaTokenTypes.tFUNTYPE => {
                  ParserUtils.eatElement(builder, ScalaElementTypes.FUN_TYPE)
                  parse(builder)
                }
                case _ => {
                  ScalaElementTypes.TYPE
                }
              }
            }
            case _ => result
          }
        } else {
          ParserUtils.eatElement(builder, ScalaElementTypes.LPARENTHIS)
          if (ScalaTokenTypes.tRPARENTHIS.equals(builder.getTokenType)){
            rightBraceProcessing
          } else {
            var res = Types.parse(builder)
            if (res.equals(ScalaElementTypes.TYPES)) {
              if (ScalaTokenTypes.tRPARENTHIS.equals(builder.getTokenType)){
                rightBraceProcessing
              } else {
                builder.error("Right brace expected")
                ScalaElementTypes.WRONGWAY
              }
            } else {
              builder.error("Types list expected")
              ScalaElementTypes.WRONGWAY
            }
          }
        }
      }
      val typeMarker = builder.mark()
      var res = subParse
      typeMarker.done(ScalaElementTypes.TYPE)
      res
    }


  }

  object Types {

  /*
  Types
  Default grammar:
  Types ::= Type {‘,’ Type}
  *******************************************
  */

    def parse(builder : PsiBuilder) : ScalaElementType = {

      def subParse : ScalaElementType = {
        var result = Type parse(builder)
        result match {
          case ScalaElementTypes.TYPE => {
            builder.getTokenType match {
              case ScalaTokenTypes.tCOMMA=> {
                ParserUtils.eatElement(builder, ScalaElementTypes.COMMA)
                subParse
              }
              case _ => {
                ScalaElementTypes.TYPES
              }
            }
          }
          case _ => result
        }
      }

      var res = ScalaElementTypes.TYPES
      val typesMarker = builder.mark()
      if (!ScalaTokenTypes.tRPARENTHIS.equals(builder.getTokenType)) {
        res = subParse
      }
      typesMarker.done(ScalaElementTypes.TYPES)
      res
    }
  }



}