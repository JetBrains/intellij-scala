package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package base

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes

/**
* @author Alexander Podkhalyuzin
* Date: 15.02.2008
*/

/*
 *   Modifier ::= LocalModifier
 *             | override
 *             | AccessModifier
 */

object Modifier {
  def parse(builder: PsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypes.kOVERRIDE => {
        builder.advanceLexer //Ate override
        return true
      }
      case _ => (LocalModifier parse builder) || (AccessModifier parse builder)
    }
  }
}