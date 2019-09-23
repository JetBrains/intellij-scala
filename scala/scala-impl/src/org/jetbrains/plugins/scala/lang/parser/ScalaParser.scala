package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.lang.{ASTNode, PsiBuilder, PsiParser}
import com.intellij.psi.tree.IElementType

final class ScalaParser extends PsiParser {

  import parsing._
  import builder._

  override def parse(rootElementType: IElementType, delegate: PsiBuilder): ASTNode = {
    implicit val builder: ScalaPsiBuilder = new ScalaPsiBuilderImpl(delegate)

    rootElementType match {
      case ScCodeBlockElementType.BlockExpression =>
        expressions.BlockExpr.parse(builder)
      case _ =>
        val rootMarker = delegate.mark()
        CompilationUnit.parse()
        rootMarker.done(rootElementType)
    }

    delegate.getTreeBuilt
  }
}
