package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

object RefineStatSeq {
  def parse(builder: PsiBuilder) {
    while (true) {
      builder.getTokenType match {
        //end of parsing when find } or builder.eof
        case ScalaTokenTypes.tRBRACE | null => return
        case ScalaTokenTypes.tLINE_TERMINATOR |
             ScalaTokenTypes.tSEMICOLON => builder.advanceLexer //not interesting case
        //otherwise parse TopStat
        case _ => {
          if (!RefineStat.parse(builder)) {
            builder error ScalaBundle.message("wrong.top.statment.declaration")
            builder.advanceLexer
          }
          else {
            builder.getTokenType match {
              case ScalaTokenTypes.tSEMICOLON |
                   ScalaTokenTypes.tLINE_TERMINATOR => builder.advanceLexer //it is good
              case null | ScalaTokenTypes.tRBRACE => return
              case _ => builder error ScalaBundle.message("semi.expected")
            }
          }
        }
      }
    }
  }
}