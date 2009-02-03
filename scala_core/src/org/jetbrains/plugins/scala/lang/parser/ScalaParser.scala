package org.jetbrains.plugins.scala.lang.parser

import com.intellij.lang.PsiParser
import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import parsing.{ParserState, Program}


class ScalaParser extends PsiParser {

  def parse(root: IElementType, builder: PsiBuilder): ASTNode = {
    val rootMarker = builder.mark
    val scriptMarker = builder.mark
    new Program parse (builder) match {
      case ParserState.SCRIPT_STATE => scriptMarker.done(ScalaElementTypes.SCALA_SCRIPT_CLASS)
      case _ => scriptMarker.drop
    }
    rootMarker.done(root)
    builder.getTreeBuilt
  }
}
