package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.lang.PsiParser
import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import parsing.builder.ScalaPsiBuilderImpl
import parsing.Program
import parsing.types.Type


class ScalaParser extends PsiParser {

  def parse(root: IElementType, builder: PsiBuilder): ASTNode = {
    val rootMarker = builder.mark
    val program: Program = new Program
    program.parse(new ScalaPsiBuilderImpl(builder))
    rootMarker.done(root)
    builder.getTreeBuilt
  }
}
