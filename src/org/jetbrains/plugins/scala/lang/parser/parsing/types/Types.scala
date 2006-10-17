package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util._

/**
Parsing various types with its names and declarations
*/
object Types{

/*
STABLE ID & PATH

Default grammar:

StableId ::= id
            | StableId ‘.’ id
            | [id ‘.’] this ‘.’ id
            | [id ’.’] super [‘[’ id ‘]’] ‘.’ id ‘.’ id

*******************************************

Path ::= StableId
        | [id ‘.’] this
        | [id ’.’] super [‘[’ id ‘]’]‘.’ id

*******************************************

FIRST(StableId) = ScalaTokenTypes.tIIDENTIFIER
union             FIRST(Path)

FIRST(Path) = ScalaTokenTypes.tIIDENTIFIER,
              ScalaTokenTypes.kTHIS,
              ScalaTokenTypes.kSUPER
*/
  // Parses StableId
  def parseStableId(builder : PsiBuilder) : Boolean = {

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
    def afterSuper(currentMarker: PsiBuilder.Marker): Boolean = {
      val nextMarker = currentMarker.precede()
      currentMarker.done(ScalaElementTypes.STABLE_ID)
      ParserUtils.eatElement(builder, ScalaElementTypes.DOT)
      if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaElementTypes.IDENTIFIER)
        if (ScalaTokenTypes.tDOT.equals(builder.getTokenType)) {
          val nextMarker1 = nextMarker.precede()
          nextMarker.done(ScalaElementTypes.STABLE_ID)
          ParserUtils.eatElement(builder, ScalaElementTypes.DOT)
            if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
              ParserUtils.eatElement(builder, ScalaElementTypes.IDENTIFIER)
              builder.getTokenType() match {
                case ScalaTokenTypes.tDOT => {
                  val nextMarker2 = nextMarker1.precede()
                  nextMarker1.done(ScalaElementTypes.STABLE_ID)
                  ParserUtils.eatElement(builder, ScalaElementTypes.DOT)
                  leftRecursion(nextMarker2)
                }
                case _ => {
                  nextMarker1.done(ScalaElementTypes.STABLE_ID)
                  true
                }
              }
            } else ParserUtils.errorToken(builder, nextMarker1, "Wrong id declaration", ScalaElementTypes.STABLE_ID)
        } else ParserUtils.errorToken(builder, nextMarker, "Wrong id declaration", ScalaElementTypes.STABLE_ID)
      } else ParserUtils.errorToken(builder, nextMarker, "Wrong id declaration", ScalaElementTypes.STABLE_ID)
    }

    /** sequence like  {'.' id } processing **/
    def leftRecursion (currentMarker: PsiBuilder.Marker): Boolean = {
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
              true
            }
          }
        }
        case _ => ParserUtils.errorToken(builder, currentMarker, "Wrong id declaration", ScalaElementTypes.STABLE_ID)
      }
    }

    def afterDotParse(currentMarker: PsiBuilder.Marker): Boolean = {
      builder.getTokenType match {
        /************** THIS ***************/
        case ScalaTokenTypes.kTHIS => {
          ParserUtils.eatElement(builder, ScalaElementTypes.THIS)
          val newMarker = currentMarker.precede()
          currentMarker.done(ScalaElementTypes.STABLE_ID)

          if (ScalaTokenTypes.tDOT.equals(builder.getTokenType)){
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
                  true
                }
              }
            } else ParserUtils.errorToken(builder, newMarker, "Wrong id declaration", ScalaElementTypes.STABLE_ID)
          } else ParserUtils.errorToken(builder, newMarker, "Wrong id declaration", ScalaElementTypes.STABLE_ID)
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
              true
            }
          }
        }
        /******************* OTHER *********************/
        case _ => ParserUtils.errorToken(builder, currentMarker, "Wrong id declaration", ScalaElementTypes.STABLE_ID)
      }
    }

    def stableIdSubParse(currentMarker: PsiBuilder.Marker) : Boolean = {
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
              true
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

  // Parses Path
  /*
  def parseStableId(builder : PsiBuilder) : Boolean = {

  }
  */




}