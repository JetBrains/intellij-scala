package org.jetbrains.plugins.scala.lang.parser.parsing.top.template

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Constructor
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Import
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Ids
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
import org.jetbrains.plugins.scala.lang.parser.parsing.types.AnnotType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Expr

/** 
* @author Alexander Podkhalyuzin
* Date: 08.02.2008
*/

/*
 *  TemplateParents ::= Constr {with AnnotType}
 */

object ClassParents {
  def parse(builder: PsiBuilder) {
    val classParentsMarker = builder.mark
    Constructor parse builder
    //Look for mixin
    while (builder.getTokenType == ScalaTokenTypes.kWITH) {
      builder.advanceLexer //Ate with
      if (!AnnotType.parse(builder)) {
        builder error ScalaBundle.message("wrong.simple.type", new Array[Object](0))
        classParentsMarker.done(ScalaElementTypes.TEMPLATE_PARENTS)
        return false
      }
    }
    classParentsMarker.done(ScalaElementTypes.TEMPLATE_PARENTS)
    return true
  }
}