package org.jetbrains.plugins.scala.lang.parser.parsing.top

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import com.intellij.lang.PsiBuilder

/**
 * User: Dmitry.Krasilschikov
 * Date: 04.10.2006
 * Time: 18:08:23
 */

class Top extends ScalaTokenTypes{

  def skipLineTerminators (builder : PsiBuilder) : Unit = {
    while ( (!builder.eof()) && ScalaTokenTypes.tWHITE_SPACE_LINE_TERMINATE.equals(builder.getTokenType)){
      builder.advanceLexer
    }
  }

  def parse(builder: PsiBuilder): Unit = {

    if (builder.getTokenType.equals(ScalaTokenTypes.kPACKAGE)) {
        new Package().parse(builder)

        skipLineTerminators(builder)
    }

    if (builder.getTokenType.equals(ScalaTokenTypes.kIMPORT)) {
        new ImportList().parse(builder)
    }

    skipLineTerminators( builder )

    if (builder.getTokenType.equals(ScalaTokenTypes.kOBJECT)
      || builder.getTokenType.equals(ScalaTokenTypes.kCLASS)
      || builder.getTokenType.equals(ScalaTokenTypes.kTRAIT)){

      TmplDef.parse(builder)
    }

  }
}