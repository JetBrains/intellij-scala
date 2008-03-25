package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.ScalaBundle

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 28.02.2008
* Time: 11:50:55
* To change this template use File | Settings | File Templates.
*/

/*
 *  CompoundType ::= AnnotType {with AnnotType} [Refinement]
 *                 | Refinement
 */

object CompoundType {
  def parse(builder: PsiBuilder): Boolean = {
    val compoundMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLINE_TERMINATOR | ScalaTokenTypes.tLBRACE => {
        if (Refinement parse builder){
          compoundMarker.drop
          return true
        }
        else {
          compoundMarker.drop
          return false
        }
      }
      case _ => {
        if (!AnnotType.parse(builder)) {
          compoundMarker.drop
          return false
        }
        else {
          var isCompound = false
          while (builder.getTokenType == ScalaTokenTypes.kWITH) {
            isCompound = true
            builder.advanceLexer //Ate with
            if (!AnnotType.parse(builder)) {
              builder error ScalaBundle.message("wrong.type", new Array[Object](0))
            }
          }
          Refinement parse builder
          if (isCompound) compoundMarker.done(ScalaElementTypes.COMPOUND_TYPE)
          else compoundMarker.drop
          return true
        }
      }
    }
  }
}