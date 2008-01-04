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
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template._
import org.jetbrains.plugins.scala.lang.parser.parsing.top._
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType

  object RefineStat {
  /*
RefineStat ::=    Dcl
                | type TypeDef
                |
  */

    def parse(builder : PsiBuilder) : Unit = {
      val rsMarker = builder.mark()
      builder.getTokenType match {
        case ScalaTokenTypes.kTYPE => {
          ParserUtils.eatElement(builder , ScalaTokenTypes.kTYPE)
          TypeDef.parseBody(builder)
          rsMarker.done(ScalaElementTypes.REFINE_STAT)
        } case ScalaTokenTypes.kVAL
               | ScalaTokenTypes.kVAR
               | ScalaTokenTypes.kDEF
               | ScalaTokenTypes.kTYPE => {
          Dcl.parseBody(builder)
          rsMarker.done(ScalaElementTypes.REFINE_STAT)
        } case _ => {
          builder.error("Wrong refinement statement")
          builder.advanceLexer()
          rsMarker.done(ScalaElementTypes.REFINE_STAT)
        }
      }
    }
  }

  object Refinements {
  /*
    Refinement ::= { [RefineStat {StatementSeparator RefineStat}] }
  */

    def parse(builder : PsiBuilder) : ScalaElementType = {
      var rMarker = builder.mark()
      ParserUtils.eatElement(builder , ScalaTokenTypes.tLBRACE)
      var flag = true
      while (!ScalaTokenTypes.tRBRACE.equals(builder.getTokenType) &&
             flag &&
             !builder.eof()
             ){
        RefineStat.parse(builder)
        builder.getTokenType match {
          case ScalaTokenTypes.tLINE_TERMINATOR
               | ScalaTokenTypes.tSEMICOLON => {
            ParserUtils.eatElement(builder , builder.getTokenType)
            flag = true
          }
          case ScalaTokenTypes.tRBRACE => {
            flag = false
          }
          case _ => {
            builder.error("Statement separator expected")
            ParserUtils.eatElement(builder , builder.getTokenType)
            flag = true
          }
        }
      }
      if (!builder.eof()) {
        ParserUtils.eatElement(builder , ScalaTokenTypes.tRBRACE)
      }
      rMarker.done(ScalaElementTypes.REFINEMENTS)
      ScalaElementTypes.REFINEMENTS
    }
  }
}