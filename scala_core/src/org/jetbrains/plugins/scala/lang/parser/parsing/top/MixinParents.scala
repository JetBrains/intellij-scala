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
import org.jetbrains.plugins.scala.lang.parser.parsing.types.AnnotType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateBody




import org.jetbrains.plugins.scala.ScalaBundle

/** 
* @author Alexander Podkhalyuzin
* Date: 06.02.2008
*/

/*
 * MixinParents ::= AnnotType {'with' AnnotType}
 */

object MixinParents {
  def parse(builder: PsiBuilder): Boolean = {
    val mixinMarker = builder.mark
    //Look for parent
    if (!AnnotType.parse(builder)) {
      builder error ScalaBundle.message("wrong.simple.type", new Array[Object](0))
      mixinMarker.done(ScalaElementTypes.TRAIT_PARENTS)
      return false
    }
    //Look for mixin
    while (builder.getTokenType == ScalaTokenTypes.kWITH) {
      builder.advanceLexer //Ate with
      if (!AnnotType.parse(builder)) {
        builder error ScalaBundle.message("wrong.simple.type", new Array[Object](0))
        mixinMarker.done(ScalaElementTypes.TRAIT_PARENTS)
        return false
      }
    }
    mixinMarker.done(ScalaElementTypes.TRAIT_PARENTS)
    return true
  }
}