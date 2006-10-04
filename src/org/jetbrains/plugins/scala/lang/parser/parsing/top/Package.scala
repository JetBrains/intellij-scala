package org.jetbrains.plugins.scala.lang.parser.parsing.top


import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import com.intellij.lang.PsiBuilder

class Package extends ScalaTokenTypes {

  def parse(builder: PsiBuilder): Unit = {

    var marker = builder.mark()

    while ( !builder.eof())
    {
        builder.advanceLexer()
    }
    new Package().parse(builder)

    //marker.done(ScalaElementTypes.PACKAGE)
  }
}