package org.jetbrains.plugins.scala.lang.parser.parsing.params

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.PsiBuilder

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
import org.jetbrains.plugins.scala.lang.parser.parsing.Constr
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.util.DebugPrint


/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 06.03.2008
* Time: 16:23:59
* To change this template use File | Settings | File Templates.
*/

/*
 * VariantTypeParam ::= ['+', '-'] TypeParam
 */

object VariantTypeParam {
  def parse(builder: PsiBuilder): Boolean = {
    val paramMarker = builder.mark
    builder.getTokenText match {
      case "+" | "-" => {
        builder.advanceLexer //Ate sign
      }
      case _ => {}
    }
    if (!TypeParam.parse(builder)) {
      paramMarker.rollbackTo
      return false
    }
    paramMarker.done(ScalaElementTypes.VARIANT_TYPE_PARAM)
    return true
  }
}