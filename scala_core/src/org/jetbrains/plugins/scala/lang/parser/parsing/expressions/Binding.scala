package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * Binding ::= id [: Type]
 */

object Binding {
  def parse(builder: PsiBuilder): Boolean = {
    val bindingMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        builder.advanceLexer //Ate id
      }
      case _ => {
        bindingMarker.drop
        return false
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tCOLON => {
        builder.advanceLexer //Ate :
        if (!Type.parse(builder)) {
          builder error ScalaBundle.message("wrong.type", new Array[Object](0))
        }
      }
      case _ => {}
    }
    bindingMarker.done(ScalaElementTypes.BINDING)
    return true
  }
}