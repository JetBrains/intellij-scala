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
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.InfixTemplate 

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
          if (doWithMarker) {
            //dotMarker.done(ScalaTokenTypes.tDOT)
            dotMarker.drop()
          }
          else {
            dotMarker.drop()
          }
          processFunction(nextMarker)
        }
      }

      /** processing [‘[’ id ‘]’] statement**/
      def parseGeneric(currentMarker: PsiBuilder.Marker): Boolean = {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tLSQBRACKET)
        if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
          if (ScalaTokenTypes.tRSQBRACKET.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tRSQBRACKET)
            true
          } else false
        } else false
      }

      /** "super" keyword processing **/
      def afterSuper(currentMarker: PsiBuilder.Marker): ScalaElementType = {
        val nextMarker = currentMarker.precede()
        currentMarker.drop()
        ParserUtils.eatElement(builder, ScalaTokenTypes.tDOT)
        if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
          if (ScalaTokenTypes.tDOT.equals(builder.getTokenType)) {
            val nextMarker1 = nextMarker.precede()
            nextMarker.drop()
            // If keyWord type encountered
            val dotMarker = builder.mark()
            ParserUtils.eatElement(builder, ScalaTokenTypes.tDOT)
              if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
                //dotMarker.done(ScalaTokenTypes.tDOT)
                dotMarker.drop
                ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
                //Console.println("token type : " + builder.getTokenType())
                builder.getTokenType() match {
                  case ScalaTokenTypes.tDOT => {
                    val nextMarker2 = nextMarker1.precede()
                    nextMarker1.done(ScalaElementTypes.STABLE_ID)
                    val dotMarker1 = builder.mark()
                    ParserUtils.eatElement(builder, ScalaTokenTypes.tDOT)
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
                               (marker1: PsiBuilder.Marker) => { builder.error("Identifier expected")
                                                                currentMarker.done(ScalaElementTypes.STABLE_ID)
                                                                ScalaElementTypes.STABLE_ID } ,
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
            ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
            builder.getTokenType match {
              case ScalaTokenTypes.tDOT => {
                val nextMarker = currentMarker.precede()
                currentMarker.done(ScalaElementTypes.STABLE_ID)
                val dotMarker = builder.mark()
                ParserUtils.eatElement(builder, ScalaTokenTypes.tDOT)
                typeProcessing(dotMarker, nextMarker, false, ScalaElementTypes.STABLE_ID, leftRecursion, true)
              }
              case _ => {
                currentMarker.done(ScalaElementTypes.STABLE_ID)
                ScalaElementTypes.STABLE_ID
              }
            }
          }
          case _ => {
            builder.error("Identifier expected")
            currentMarker.done(ScalaElementTypes.STABLE_ID)
            ScalaElementTypes.STABLE_ID
            // ParserUtils.errorToken(builder, currentMarker, "Wrong id declaration", ScalaElementTypes.STABLE_ID)
          }
        }
      }

      def afterDotParse(currentMarker: PsiBuilder.Marker): ScalaElementType = {
        builder.getTokenType match {
          /************** THIS ***************/
          case ScalaTokenTypes.kTHIS => {
            ParserUtils.eatElement(builder, ScalaTokenTypes.kTHIS)
            if (ScalaTokenTypes.tDOT.equals(builder.getTokenType)){
              val dotMarker = builder.mark()
              ParserUtils.eatElement(builder, ScalaTokenTypes.tDOT)
              if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)){
                val newMarker = currentMarker.precede()
                currentMarker.drop()
                //dotMarker.done(ScalaTokenTypes.tDOT)
                dotMarker.drop
                ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
                builder.getTokenType match {
                  case ScalaTokenTypes.tDOT => {
                    val nextMarker = newMarker.precede()
                    newMarker.done(ScalaElementTypes.STABLE_ID)
                    val dotMarker = builder.mark()
                    ParserUtils.eatElement(builder, ScalaTokenTypes.tDOT)
                    typeProcessing(dotMarker, nextMarker, false, ScalaElementTypes.STABLE_ID, leftRecursion, true)
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
                               (marker1: PsiBuilder.Marker) => { builder.error("Identifier expected")
                                                                 currentMarker.done(ScalaElementTypes.STABLE_ID)
                                                                 ScalaElementTypes.STABLE_ID },
                               false)
              }
            } else { 
              currentMarker.done(ScalaElementTypes.PATH)
              ScalaElementTypes.PATH
            }
          }
          /***************** SUPER ****************/
          case ScalaTokenTypes.kSUPER => {
            ParserUtils.eatElement(builder, ScalaTokenTypes.kSUPER)
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
            ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
            builder.getTokenType match {
              case ScalaTokenTypes.tDOT => {
                val nextMarker = currentMarker.precede()
                currentMarker.done(ScalaElementTypes.STABLE_ID)
                val dotMarker = builder.mark()
                ParserUtils.eatElement(builder, ScalaTokenTypes.tDOT)
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
            ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
            builder.getTokenType match {
              case ScalaTokenTypes.tDOT => {
                val nextMarker = currentMarker.precede()
                currentMarker.done(ScalaElementTypes.STABLE_ID)
                val dotMarker = builder.mark()
                ParserUtils.eatElement(builder, ScalaTokenTypes.tDOT)  
                typeProcessing(dotMarker, nextMarker, false, ScalaElementTypes.STABLE_ID, afterDotParse, true)
              }
              case _ => {
                //with one id only
                currentMarker.done(ScalaElementTypes.STABLE_ID)
                ScalaElementTypes.STABLE_ID_ID
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
      if (result.equals(ScalaElementTypes.STABLE_ID) || result.equals(ScalaElementTypes.STABLE_ID_ID))
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

        var myResult = ScalaElementTypes.WRONGWAY

        def close (msg: String) = {
          builder.error(msg)
          ParserUtils.rollPanicToBrace(builder, ScalaTokenTypes.tLBRACE, ScalaTokenTypes.tRBRACE)
          currentMarker.drop()
          ScalaElementTypes.CLOSED
        }

        if (ScalaTokenTypes.tLBRACE.equals(builder.getTokenType)) {
          val um = builder.mark()
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLBRACE)
          // {} - unit
          if (ScalaTokenTypes.tRBRACE.equals(builder.getTokenType)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tRBRACE)
            um.done(ScalaElementTypes.UNIT)
            ScalaElementTypes.SIMPLE_TYPE
          } else {
            um.drop()
            var res1 = Type parse (builder)
            if (res1.equals(ScalaElementTypes.TYPE)) {
              builder.getTokenType match {
                // Unary type {T, }
                case ScalaTokenTypes.tCOMMA => {
                  ParserUtils.eatElement(builder, ScalaTokenTypes.tCOMMA)
                  // Really, unary type {T,}
                  if (ScalaTokenTypes.tRBRACE.equals(builder.getTokenType)) {
                    ParserUtils.eatElement(builder, ScalaTokenTypes.tRBRACE)
                    ScalaElementTypes.SIMPLE_TYPE
                  } else {
                    // TupleN type with N >= 2
                    val res2 = Types.parse(builder, true)
                    if (ScalaElementTypes.TYPES.equals(res2)) {
                      builder.getTokenType match {
                        case ScalaTokenTypes.tCOMMA => {
                          ParserUtils.eatElement(builder, ScalaTokenTypes.tCOMMA)
                          if (ScalaTokenTypes.tRBRACE.equals(builder.getTokenType)) {
                            ParserUtils.eatElement(builder, ScalaTokenTypes.tRBRACE)
                            ScalaElementTypes.SIMPLE_TYPE
                          } else close("} expected")
                        }
                        case ScalaTokenTypes.tRBRACE => {
                          ParserUtils.eatElement(builder, ScalaTokenTypes.tRBRACE)
                          ScalaElementTypes.SIMPLE_TYPE
                        }
                        case _ => close("} expected")
                      }
                    } else close("Wrong type")
                  }
                }
                case _ => close("Wrong type")
              }
            } else close("Wrong type")
          }
        }
        /* | ‘(’ Type ’)’ */
        else if (ScalaTokenTypes.tLPARENTHIS.equals(builder.getTokenType)) { // Try to parse '(' Type ')' statement
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLPARENTHIS)
          var res1 = Type parse (builder)
          if (res1.equals(ScalaElementTypes.TYPE)) {
            builder.getTokenType match {
              case ScalaTokenTypes.tRPARENTHIS => {
                ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHIS)
                ScalaElementTypes.SIMPLE_TYPE
              }
              // For ([Types])
              case ScalaTokenTypes.tCOMMA => {
                currentMarker.rollbackTo()
                ScalaElementTypes.WRONGWAY
              }
              case _ => {
                close(") expected")
              }
            }
          } else {
            close("Wrong type")
          }
        }
        // If Stable Identifier or Path identified parsed...
        else if ( {
          var result = StableId.parse(builder)
          myResult = result
          !result.equals(ScalaElementTypes.WRONGWAY)
        } ){
          builder.getTokenType match {
            case ScalaTokenTypes.tDOT => {
              ParserUtils.eatElement(builder, ScalaTokenTypes.tDOT)
              builder.getTokenType match {
                case ScalaTokenTypes.kTYPE => {
                  ParserUtils.eatElement(builder, ScalaTokenTypes.kTYPE)
                  ScalaElementTypes.SIMPLE_TYPE
                }
                case _ => ParserUtils.errorToken(builder, currentMarker, "Wrong type", ScalaElementTypes.SIMPLE_TYPE)
              }
            }
            case _ => {
              if (myResult.equals(ScalaElementTypes.STABLE_ID) || myResult.equals(ScalaElementTypes.STABLE_ID_ID)){
                ScalaElementTypes.SIMPLE_TYPE
              } else ParserUtils.errorToken(builder, currentMarker, "Wrong type", ScalaElementTypes.SIMPLE_TYPE)
            }
          }
        } else ParserUtils.errorToken(builder, currentMarker, "Wrong type", ScalaElementTypes.SIMPLE_TYPE)
      }

      /*
      * Left recursion for statements like:
      *   SimpleType TypeArgs
      *   | SimpleType ‘#’ id
      */
      def leftRecursion(currentMarker : PsiBuilder.Marker) : ScalaElementType = {
        builder.getTokenType match {
          case ScalaTokenTypes.tINNER_CLASS => {
            val nextMarker = currentMarker.precede()
            currentMarker.done(ScalaElementTypes.SIMPLE_TYPE)
            ParserUtils.eatElement(builder, ScalaTokenTypes.tINNER_CLASS)
            builder.getTokenType match {
              case ScalaTokenTypes.tIDENTIFIER => {
                ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
                leftRecursion(nextMarker)
              }
              case _ => ParserUtils.errorToken(builder, nextMarker, "Wrong type", ScalaElementTypes.SIMPLE_TYPE)
            }
          }
          case ScalaTokenTypes.tLSQBRACKET => {
            val nextMarker = currentMarker.precede()
            currentMarker.done(ScalaElementTypes.SIMPLE_TYPE)
            val res2 = TypeArgs parse(builder)
            if (res2.equals(ScalaElementTypes.TYPE_ARGS)) leftRecursion(nextMarker)
            else {
              nextMarker.rollbackTo()
              ScalaElementTypes.WRONGWAY  
            }
          }
          case _ => {
            //currentMarker.done(ScalaElementTypes.SIMPLE_TYPE)
            currentMarker.drop
            ScalaElementTypes.SIMPLE_TYPE
          }
        }
      }

      val simpleMarker = builder.mark()
      var res = simpleTypeSubParse(simpleMarker) // Parsed base for recursion
      if (!res.equals(ScalaElementTypes.WRONGWAY)){
        if (!res.equals(ScalaElementTypes.CLOSED)){
          leftRecursion(simpleMarker)
        } else ScalaElementTypes.SIMPLE_TYPE
      } else res
    }

  }

  object CompoundType {
  /*
  CompoundType
  Default grammar:
  CompoundType ::= SimpleType {with SimpleType} [Refinement]
  *******************************************
  */
    def parse(builder : PsiBuilder) : ScalaElementType = {
      val type1Marker = builder.mark()

      def subParse : ScalaElementType = {
        var result = SimpleType parse(builder)
        result match {
          case ScalaElementTypes.SIMPLE_TYPE => {
            builder.getTokenType match {
              case ScalaTokenTypes.kWITH => {
                ParserUtils.eatElement(builder, ScalaTokenTypes.kWITH)
                subParse
              }
              case _ => {
                if (ScalaTokenTypes.tLBRACE.equals(builder.getTokenType)) {
                  Refinements.parse(builder)
                }
                type1Marker.done(ScalaElementTypes.COMPOUND_TYPE)
                ScalaElementTypes.COMPOUND_TYPE
              }
            }
          }
          case _ => {
            builder.error("Wrong simple type")
            if (ScalaTokenTypes.tLBRACE.equals(builder.getTokenType)) {
                  Refinements.parse(builder)
            }
            type1Marker.done(ScalaElementTypes.COMPOUND_TYPE)
            ScalaElementTypes.COMPOUND_TYPE
          }
        }
      }

      var res = SimpleType parse(builder)
      res match {
        case ScalaElementTypes.SIMPLE_TYPE => {
          builder.getTokenType match {
            case ScalaTokenTypes.kWITH => {
              ParserUtils.eatElement(builder, ScalaTokenTypes.kWITH)
              subParse
            }
            case _ => {
              if (ScalaTokenTypes.tLBRACE.equals(builder.getTokenType)) {
                Refinements.parse(builder)
              }
              type1Marker.done(ScalaElementTypes.COMPOUND_TYPE)
              ScalaElementTypes.COMPOUND_TYPE
            }
          }
        }
        case _ => {
          type1Marker.rollbackTo()
          ScalaElementTypes.WRONGWAY
        }
      }

    }

  }

  object InfixType extends InfixTemplate(ScalaElementTypes.INFIX_TYPE, CompoundType.parse)


  object Type {

  /*
  Type
  Default grammar:
  Type ::= CompoundType ‘=>’ Type
           | ‘(’ [Types] ‘)’ ‘=>’ Type
           | CompoundType
  *******************************************
  */
    def parse(builder : PsiBuilder) : ScalaElementType = {

      var typeMarker = builder.mark()

      // If ')' symbol - the end of list of parameter list encountered
      def rightBraceProcessing(typesMarker: PsiBuilder.Marker) : ScalaElementType = {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHIS)
        typesMarker.done(ScalaElementTypes.TYPES)
        if (ScalaTokenTypes.tFUNTYPE.equals(builder.getTokenType)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.tFUNTYPE)
          parse(builder)
          typeMarker.done(ScalaElementTypes.TYPE)
          ScalaElementTypes.TYPE
        } else {
          builder.error(" => expected")
          typeMarker.done(ScalaElementTypes.TYPE)
          ScalaElementTypes.TYPE
        }
      }

      def subParse : ScalaElementType = {
        // Suppose, this is rule that begins from InfixType statement

        var result = InfixType parse(builder)

        result match {
          case ScalaElementTypes.INFIX_TYPE |
               ScalaElementTypes.SIMPLE_TYPE |
               ScalaElementTypes.COMPOUND_TYPE => {
            builder.getTokenType match {
              case ScalaTokenTypes.tFUNTYPE => {
                ParserUtils.eatElement(builder, ScalaTokenTypes.tFUNTYPE)
                parse(builder)
                typeMarker.done(ScalaElementTypes.TYPE)
                ScalaElementTypes.TYPE
              }
              case _ => {
                typeMarker.drop()
                ScalaElementTypes.TYPE
              }
            }
          }
          case _ => {
            // Suppose, that it is statement that begins form ([Types])
            if (ScalaTokenTypes.tLPARENTHIS.equals(builder.getTokenType)){

              val typesMarker = builder.mark() // Eat types of parameters
              def end(msg: String) = {
                builder.error(msg)
                ParserUtils.rollPanicToBrace(builder, ScalaTokenTypes.tLPARENTHIS, ScalaTokenTypes.tRPARENTHIS)
                typesMarker.drop()
                typeMarker.done(ScalaElementTypes.TYPE)
                ScalaElementTypes.TYPE
              }

              def argProcess (elem: ScalaElementType,
                              result: => IElementType,
                              msg: String) = {
                var res = result
                if (res.equals(elem)) {
                  if (ScalaTokenTypes.tRPARENTHIS.equals(builder.getTokenType)){
                    rightBraceProcessing(typesMarker)
                  } else end("Right brace expected")
                } else end(msg)
              }

              ParserUtils.eatElement(builder, ScalaTokenTypes.tLPARENTHIS)
              if (ScalaTokenTypes.tFUNTYPE.equals(builder.getTokenType)) {
                ParserUtils.eatElement(builder, ScalaTokenTypes.tFUNTYPE)
                argProcess(ScalaElementTypes.TYPE,
                           Type.parse(builder),
                           "Type expected")
              } else {
                if (ScalaTokenTypes.tRPARENTHIS.equals(builder.getTokenType)){
                  rightBraceProcessing(typesMarker)
                } else {
                  argProcess(ScalaElementTypes.TYPES,
                           Types.parse(builder, false),
                           "Types list expected")
                }
              }
            } else {
              builder.error("Wrong type")
              typeMarker.rollbackTo()
              ScalaElementTypes.WRONGWAY
            }
          }
        }
      }

      if (ScalaTokenTypes.tLPARENTHIS.equals(builder.getTokenType)){
        var rMarker = builder.mark()
        val typesMarker = builder.mark() // Eat types of parameters
        ParserUtils.eatElement(builder, ScalaTokenTypes.tLPARENTHIS)
        if (ScalaTokenTypes.tRPARENTHIS.equals(builder.getTokenType)){
          rMarker.drop()
          rightBraceProcessing(typesMarker)
        } else {
          rMarker.rollbackTo()
          subParse
        }
      } else {
        subParse
      }
    }

  }

  object Types {

  /*
  Types
  Default grammar:
  Types ::= Type {‘,’ Type}
  *******************************************
  */

    def parse(builder : PsiBuilder, forTuple: Boolean) : ScalaElementType = {

      val myTuple = forTuple

      def subParse : ScalaElementType = builder.getTokenType match {
        case ScalaTokenTypes.tCOMMA=> {
          val rb = builder.mark()
          ParserUtils.eatElement(builder, ScalaTokenTypes.tCOMMA)
          val result = Type parse(builder)
          if (!ScalaElementTypes.WRONGWAY.equals(result)){
            rb.drop()
            subParse
          } else {
            if (myTuple) {
              rb.rollbackTo()
              ScalaElementTypes.TYPES
            } else {
              rb.drop()
              ScalaElementTypes.WRONGWAY
            }
          }
        }
        case _ => {
          ScalaElementTypes.TYPES
        }
      }

      val res =  Type parse(builder)
      if (!ScalaElementTypes.WRONGWAY.equals(res)) {
        subParse
      }
      else {
        res
      }
    }
  }


  object TypeArgs {

  /*
  Type Arguments
  Default grammar:
  TypeArgs ::= ‘[’ Types ‘]’
  *******************************************
  */

    def parse(builder : PsiBuilder) : ScalaElementType = {

      var result = ScalaElementTypes.TYPE_ARGS
      val typeArgsMarker = builder.mark()

      def closeBracket = builder.getTokenType match {
        case ScalaTokenTypes.tRSQBRACKET => {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tRSQBRACKET)
          result = ScalaElementTypes.TYPE_ARGS
        }
        case _ => {
          builder.error("] expected")
          ParserUtils.rollPanicToBrace(builder, ScalaTokenTypes.tLSQBRACKET, ScalaTokenTypes.tRSQBRACKET)
          result = ScalaElementTypes.TYPE_ARGS
        }
      }

      if (ScalaTokenTypes.tLSQBRACKET.equals(builder.getTokenType)){
        ParserUtils.eatElement(builder, ScalaTokenTypes.tLSQBRACKET)
        if (ScalaTokenTypes.tUNDER.equals(builder.getTokenType)){
          ParserUtils.eatElement(builder, builder.getTokenType)
          closeBracket
        } else {
          var res = Types parse(builder, false)
          if (res.equals(ScalaElementTypes.TYPES)) {
            closeBracket
          } else {
            builder.error("Wrong type")
            ParserUtils.rollPanicToBrace(builder, ScalaTokenTypes.tLSQBRACKET, ScalaTokenTypes.tRSQBRACKET)
            result = ScalaElementTypes.TYPE_ARGS
          }
        }
      } else {
        builder.error ("[ expected")
        result = ScalaElementTypes.WRONGWAY
      }
      if (result.equals(ScalaElementTypes.TYPE_ARGS)) typeArgsMarker.done(ScalaElementTypes.TYPE_ARGS)
        else typeArgsMarker.rollbackTo()
      result
    }
  }

}