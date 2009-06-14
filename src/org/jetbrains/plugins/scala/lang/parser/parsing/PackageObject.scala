package org.jetbrains.plugins.scala.lang.parser.parsing


import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import top.ObjectDef

/**
 * @author ilyas
 */

object PackageObject {
  def parse(builder: PsiBuilder) : Boolean = {
    val marker = builder.mark
    if (builder.getTokenType != ScalaTokenTypes.kPACKAGE) {
      marker.drop
      return false
    }
    // Eat `package modifier'
    builder.advanceLexer

    if (builder.getTokenType != ScalaTokenTypes.kOBJECT) {
      marker.drop
      return false
    }
    // Eat `object' modifier
    builder.advanceLexer

    if (ObjectDef parse builder) {
      marker.done(ScalaElementTypes.OBJECT_DEF)
    } else {
      marker.drop
    }
    true
  }
}