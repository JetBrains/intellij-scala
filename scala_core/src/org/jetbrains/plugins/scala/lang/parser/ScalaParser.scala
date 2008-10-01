package org.jetbrains.plugins.scala.lang.parser

import com.intellij.lang.PsiParser
import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import decompiler.ScalaDecompiler
import org.jetbrains.plugins.scala.lang.parser.parsing.Program;

class ScalaParser extends PsiParser {

  def parse(root: IElementType, builder: PsiBuilder): ASTNode = {
//    builder.setDebugMode(true)
    var rootMarker = builder.mark()
    new Program parse (builder)
    rootMarker.done(root)
    builder.getTreeBuilt()
  }
}
