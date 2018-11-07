package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.lang.{ASTNode, PsiBuilder, PsiParser}
import com.intellij.psi.tree.IElementType

final class ScalaParser extends PsiParser {

  import parsing._

  def parse(root: IElementType, delegate: PsiBuilder): ASTNode = {
    val builderImpl = new builder.ScalaPsiBuilderImpl(delegate)

    root match {
      case ScCodeBlockElementType.BlockExpression =>
        expressions.BlockExpr.parse(builderImpl)
      case _ =>
        val rootMarker = delegate.mark
        Program.parse(builderImpl)
        rootMarker.done(root)
    }

    delegate.getTreeBuilt
  }
}
