package org.jetbrains.plugins.scala.lang.parser

import com.intellij.lang.PsiParser
import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import parsing.Program
import parsing.types.Type


class ScalaParser extends PsiParser {

  def parse(root: IElementType, builder: PsiBuilder): ASTNode = {
    val rootMarker = builder.mark
    new Program parse (builder)
    rootMarker.done(root)
    builder.getTreeBuilt
  }
}
