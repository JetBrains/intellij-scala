package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator
import org.jetbrains.plugins.scala.lang.parser.parsing.statements.Dcl

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 28.02.2008
* Time: 15:10:33
* To change this template use File | Settings | File Templates.
*/

/*
 *  Types ::= Type {',' Type}
 */

object Types {
  def parse(builder: PsiBuilder): Boolean ={
    SimpleType.isTuple = false
    val typesMarker = builder.mark
    if (!Type.parse(builder)) {
      typesMarker.drop
      return false
    }
    var exit = true
    while (exit && builder.getTokenType == ScalaTokenTypes.tCOMMA) {
      SimpleType.isTuple = true
      builder.advanceLexer //Ate ,
      if (!Type.parse(builder)) {
        exit = false
        //builder error ScalaBundle.message("wrong.type",new Array[Object](0))
      }
    }
    if (SimpleType.isTuple) typesMarker.done(ScalaElementTypes.TYPES)
    else typesMarker.drop
    return true
  }
}