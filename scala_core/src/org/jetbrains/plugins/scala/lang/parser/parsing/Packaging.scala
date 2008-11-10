package org.jetbrains.plugins.scala.lang.parser.parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Import
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableId
import com.intellij.psi.tree.IElementType
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.ScalaBundle
//import org.jetbrains.plugins.scala.ScalaBundleImpl
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator
import org.jetbrains.plugins.scala.lang.parser.parsing.top.Qual_Id

/** 
* @author Alexander Podkhalyuzin
* Date: 05.02.2008
*/

/*
 * Packaging := 'package' QualId [nl] '{' TopStatSeq '}'
 */

object Packaging {
  def parse(builder: PsiBuilder):Boolean = {
    val packMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kPACKAGE => {
        builder.advanceLexer //Ate package
        if (!(Qual_Id parse builder)) {
          packMarker.drop
          return false
        }
        //parsing body of packaging
        builder.getTokenType match {
          case ScalaTokenTypes.tLBRACE => {
            builder.advanceLexer //Ate '{'
            //parse packaging body
            TopStatSeq parse (builder, true)
            //Look for '}'
            builder.getTokenType match {
              case ScalaTokenTypes.tRBRACE => {
                builder.advanceLexer //Ate '}'
                packMarker.done(ScalaElementTypes.PACKAGING)
                return true
              }
              case _ => {
                builder error ScalaBundle.message("rbrace.expected")
                packMarker.done(ScalaElementTypes.PACKAGING)
                return true
              }
            }
          }
          case ScalaTokenTypes.tLINE_TERMINATOR => {
            if (!LineTerminator(builder.getTokenText)) {
              builder.advanceLexer //Ate nl
              builder error ScalaBundle.message("lbrace.expected")
              packMarker.done(ScalaElementTypes.PACKAGING)
              return true
            }
            else {
              builder.advanceLexer //Ate nl
              builder.getTokenType match {
                case ScalaTokenTypes.tLBRACE => {
                   builder.advanceLexer //Ate '{'
                  //parse packaging body
                  TopStatSeq parse (builder, true)
                  //Look for '}'
                  builder.getTokenType match {
                    case ScalaTokenTypes.tRBRACE => {
                      builder.advanceLexer //Ate '}'
                      packMarker.done(ScalaElementTypes.PACKAGING)
                      return true
                    }
                    case _ => {
                      builder error ScalaBundle.message("rbrace.expected")
                      packMarker.done(ScalaElementTypes.PACKAGING)
                      return true
                    }
                  }
                }
                case _ => {
                  builder error ScalaBundle.message("lbrace.expected")
                  packMarker.done(ScalaElementTypes.PACKAGING)
                  return true
                }
              }
            }
          }
          case _ => {
            builder error ScalaBundle.message("lbrace.expected")
            packMarker.done(ScalaElementTypes.PACKAGING)
            return true
          }
        }
      }
      case _ => {
        //this code shouldn't be reachabled, if it is, this is unexpexted error
        builder error ScalaBundle.message("unreachable.error")
        packMarker.drop
        return false
      }
    }
  }
}