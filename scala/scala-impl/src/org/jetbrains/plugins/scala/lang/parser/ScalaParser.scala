package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.lang.{ASTNode, LightPsiParser, PsiBuilder, PsiParser}
import com.intellij.psi.tree.IElementType

final class ScalaParser(isScala3: Boolean) extends PsiParser with LightPsiParser {

  import parsing._
  import builder._

  override def parseLight(rootElementType: IElementType, delegate: PsiBuilder): Unit = {
    implicit val builder: ScalaPsiBuilder = new ScalaPsiBuilderImpl(delegate, isScala3)

    rootElementType match {
      case ScCodeBlockElementType.BlockExpression =>
        expressions.BlockExpr()
      case _ =>
        val rootMarker = delegate.mark()
        CompilationUnit()
        rootMarker.done(rootElementType)
    }
  }

  override def parse(rootElementType: IElementType, delegate: PsiBuilder): ASTNode = {
    parseLight(rootElementType, delegate)
    delegate.getTreeBuilt
  }
}
