package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType

/*
Special Class for StableId in "immport statmenet" parsing
*/


object StableIdInImport {

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
    * Process statements like *._
    *                       or *._{...
    *                       or *.id_
    * for import statment needs
    */
    def specialProcessing( dotMarker: PsiBuilder.Marker,
                        nextMarker: PsiBuilder.Marker,
                        doneOrDrop: Boolean,
                        elem: ScalaElementType,
                        processFunction: PsiBuilder.Marker => ScalaElementType,
                        doWithMarker: Boolean
                      ): ScalaElementType = {
      // if ._ or .{ encoutered
      if (ScalaTokenTypes.tUNDER.equals(builder.getTokenType) ||
          ScalaTokenTypes.tLBRACE.equals(builder.getTokenType)) {
        dotMarker.rollbackTo()
        if (doneOrDrop) nextMarker.done(elem)
        else nextMarker.drop()
        elem
      } else
      // if .id encoutered
      if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)){
        val mileMarker = builder.mark()
        builder.advanceLexer
        if (ScalaTokenTypes.tDOT.equals(builder.getTokenType)) { //continue parse StableId
          mileMarker.rollbackTo()
          if (doWithMarker) dotMarker.done(ScalaElementTypes.DOT)
          else dotMarker.drop()
          processFunction(nextMarker)
        } else { // If it was the last identifier
          mileMarker.rollbackTo()
          dotMarker.rollbackTo()
          if (doneOrDrop) nextMarker.done(elem)
          else nextMarker.drop()
          elem
        }
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
                  specialProcessing(dotMarker1, nextMarker2, false, ScalaElementTypes.STABLE_ID, leftRecursion, true)
                }
                case _ => {
                  nextMarker1.done(ScalaElementTypes.STABLE_ID)
                  ScalaElementTypes.STABLE_ID
                }
              }
            } else {
              specialProcessing(dotMarker,
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
              specialProcessing(dotMarker, nextMarker, false, ScalaElementTypes.STABLE_ID, leftRecursion, true)
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
              specialProcessing(dotMarker,
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
              specialProcessing(dotMarker, nextMarker, false, ScalaElementTypes.STABLE_ID, leftRecursion, true)
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
              specialProcessing(dotMarker, nextMarker, false, ScalaElementTypes.STABLE_ID, afterDotParse, true)
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
