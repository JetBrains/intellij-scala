package org.jetbrains.plugins.scala.lang.parser

import com.intellij.lang.{ASTNode, LightPsiParser, PsiBuilder, PsiParser}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.Tracing

class ScalaParser(isScala3: Boolean) extends PsiParser with LightPsiParser {

  import parsing._
  import builder._

  /** intended to be overridden in case a user of this class wants
   * another implementation of `ScalaPsiBuilder` */
  def mkScalaPsiBuilder(delegate: PsiBuilder, isScala3: Boolean): ScalaPsiBuilder =
    new ScalaPsiBuilderImpl(delegate, isScala3)

  override def parseLight(rootElementType: IElementType, delegate: PsiBuilder): Unit = {
    implicit val builder: ScalaPsiBuilder = mkScalaPsiBuilder(delegate, isScala3)

    rootElementType match {
      case ScCodeBlockElementType.BlockExpression =>
        expressions.BlockExpr()
      case _ =>
        val rootMarker = delegate.mark()
        CompilationUnit()
        rootMarker.done(rootElementType)
    }

    Tracing.parser(builder)
  }

  override def parse(rootElementType: IElementType, delegate: PsiBuilder): ASTNode = {
    parseLight(rootElementType, delegate)
    delegate.getTreeBuilt
  }
}
