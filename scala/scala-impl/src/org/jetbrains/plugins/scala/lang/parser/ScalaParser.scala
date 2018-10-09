package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.lang.{ASTNode, PsiBuilder, PsiParser}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.Program
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilderImpl
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.BlockExpr

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
    builder.getTreeBuilt
  }
}
