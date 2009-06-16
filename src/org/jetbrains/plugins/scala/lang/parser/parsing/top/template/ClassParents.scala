package org.jetbrains.plugins.scala.lang.parser.parsing.top.template

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Constructor
import org.jetbrains.plugins.scala.lang.parser.parsing.types.AnnotType
/**
* @author Alexander Podkhalyuzin
* Date: 08.02.2008
*/

/*
 *  TemplateParents ::= Constr {with AnnotType}
 */

object ClassParents {
  def parse(builder: PsiBuilder): Boolean = {
    val classParentsMarker = builder.mark
    if (!Constructor.parse(builder)) {
      classParentsMarker.drop
      return false
    }
    //Look for mixin
    while (builder.getTokenType == ScalaTokenTypes.kWITH) {
      builder.advanceLexer //Ate with
      if (!AnnotType.parse(builder)) {
        builder error ScalaBundle.message("wrong.simple.type")
        classParentsMarker.done(ScalaElementTypes.CLASS_PARENTS)
        return true
      }
    }
    classParentsMarker.done(ScalaElementTypes.CLASS_PARENTS)
    return true
  }
}