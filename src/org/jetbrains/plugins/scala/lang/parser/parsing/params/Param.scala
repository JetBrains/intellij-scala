package org.jetbrains.plugins.scala.lang.parser.parsing.params

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.PsiBuilder

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
import org.jetbrains.plugins.scala.lang.parser.parsing.Constr
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._
import org.jetbrains.plugins.scala.lang.parser.parsing.types._
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.util.DebugPrint


/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 06.03.2008
* Time: 16:24:41
* To change this template use File | Settings | File Templates.
*/

/*
 * Param ::= {Annotation} id [':' ParamType]
 */

object Param {
  def parse(builder: PsiBuilder): Boolean = {
    val paramMarker = builder.mark
    //parse annotations
    val annotationsMarker = builder.mark
    while (Annotation.parse(builder)) {}
    annotationsMarker.done(ScalaElementTypes.ANNOTATIONS)
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        builder.advanceLexer //Ate id
      }
      case _ => {
        paramMarker.rollbackTo
        return false
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tCOLON => {
        builder.advanceLexer //Ate :
        if (!ParamType.parse(builder)) builder error ErrMsg("wrong.type")
      }
      case _ => {}
    }
    paramMarker.done(ScalaElementTypes.PARAM)
    return true
  }
}