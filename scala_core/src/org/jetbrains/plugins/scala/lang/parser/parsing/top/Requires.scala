package org.jetbrains.plugins.scala.lang.parser.parsing.top

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IChameleonElementType
import com.intellij.psi.tree.TokenSet

import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types.SimpleType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateBody




import org.jetbrains.plugins.scala.ScalaBundle

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 06.02.2008
* Time: 17:52:50
* To change this template use File | Settings | File Templates.
*/

object Requires {
  def parse(builder: PsiBuilder): Boolean = {
    val requiresMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kREQUIRES => {
        builder.advanceLexer //Ate requires
        //parsing simple type
        if (BNF.firstSimpleType.contains(builder.getTokenType)) {
          SimpleType parse builder
        }
        else {
          builder error ScalaBundle.message("simple.type.expected.requires", new Array[Object](0))
        }
        requiresMarker.done(ScalaElementTypes.REQUIRES_BLOCK)
        return true
      }
      case _ => {
        requiresMarker.drop
        return false
      }
    }
  }
}