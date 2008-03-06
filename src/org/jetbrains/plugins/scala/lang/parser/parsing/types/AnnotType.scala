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
* Date: 06.02.2008
* Time: 18:36:27
* To change this template use File | Settings | File Templates.
*/

/*
 * AnnotType ::= {Annotation} SimpleType
 */

object AnnotType {
  def parse(builder: PsiBuilder): Boolean = {
    val annotMarker = builder.mark
    //TODO: parse annotations
    //parse Simple type
    if (BNF.firstSimpleType.contains(builder.getTokenType)){
      SimpleType parse builder
      annotMarker.done(ScalaElementTypes.ANNOT_TYPE)
      return true
    }
    else {
      annotMarker.rollbackTo
      return false
    }
  }
}