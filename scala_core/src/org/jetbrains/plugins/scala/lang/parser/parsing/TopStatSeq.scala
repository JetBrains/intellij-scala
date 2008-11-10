package org.jetbrains.plugins.scala.lang.parser.parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableId
import com.intellij.psi.tree.IElementType
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.ScalaBundle

/**
* @author Alexander Podkhalyuzin
* Date: 05.02.2008
*/

/*
*  TopStatSeq ::= TopStat {semi TopStat}
*/

object TopStatSeq {
  def parse(builder: PsiBuilder): Unit = parse(builder, false)
  def parse(builder: PsiBuilder, waitBrace: Boolean): Unit ={
    while (true) {
      builder.getTokenType match {
        //end of parsing when find } or builder.eof
        case ScalaTokenTypes.tRBRACE if waitBrace => return
        case null => return
        case ScalaTokenTypes.tLINE_TERMINATOR |
             ScalaTokenTypes.tSEMICOLON => builder.advanceLexer //not interesting case
        //otherwise parse TopStat
        case _ => {
          if (!TopStat.parse(builder)) {
            builder error ScalaBundle.message("wrong.top.statment.declaration")
            builder.advanceLexer
          }
          else {
            builder.getTokenType match {
              case ScalaTokenTypes.tSEMICOLON |
                   ScalaTokenTypes.tLINE_TERMINATOR => builder.advanceLexer //it is good
              case ScalaTokenTypes.tRBRACE if waitBrace => return
              case null => return
              case _ => builder error ScalaBundle.message("semi.expected")
            }
          }
        }
      }
    }
  }
}