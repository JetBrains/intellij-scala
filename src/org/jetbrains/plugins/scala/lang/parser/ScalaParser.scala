package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.lang.PsiParser
import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import parsing.builder.ScalaPsiBuilderImpl
import parsing.expressions.{BlockExpr, Block}
import parsing.Program

class ScalaParser extends PsiParser {

  def parse(root: IElementType, builder: PsiBuilder): ASTNode = {
    root match {
      case ScalaElementTypes.BLOCK_EXPR =>
        BlockExpr.parse(new ScalaPsiBuilderImpl(builder))
      case _ =>
        val rootMarker = builder.mark
        Program.parse(new ScalaPsiBuilderImpl(builder))
        rootMarker.done(root)
    }
    try {
      builder.getTreeBuilt
    }
    catch {
      case t: Throwable if t.getMessage != null &&
        t.getMessage.contains("unbalanced") => throw new RuntimeException("Error during parsing: " + builder.getOriginalText, t)
    }
  }
}
