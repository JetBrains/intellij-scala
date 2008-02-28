package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.InfixTemplate
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator
import org.jetbrains.plugins.scala.lang.parser.parsing.statements.Dcl

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 28.02.2008
* Time: 14:35:19
* To change this template use File | Settings | File Templates.
*/

/*
 * ExistentialDclSeq ::= ExistentialDcl {semi ExistentialDcl}
 *
 * ExistentialDcl ::= 'type' TypeDcl
 *                  | 'val' ValDcl
 */

object ExistentialDclSeq {
  def parse(builder: PsiBuilder) {
    builder.getTokenType match {
      case ScalaTokenTypes.kTYPE | ScalaTokenTypes.kVAL => {
        Dcl parse (builder,false)
      }
      case _ => {
        builder error ScalaBundle.message("wrong.existential.declaration", new Array[Object](0))
        return
      }
    }
    while (builder.getTokenType == ScalaTokenTypes.tSEMICOLON
          || builder.getTokenType == ScalaTokenTypes.tLINE_TERMINATOR) {
      builder.advanceLexer //Ate semi
      builder.getTokenType match {
        case ScalaTokenTypes.kTYPE | ScalaTokenTypes.kVAL => {
          Dcl parse (builder,false)
        }
        case _ => {
          builder error ScalaBundle.message("wrong.existential.declaration", new Array[Object](0))
        }
      }
    }
  }
}