package org.jetbrains.plugins.scala.lang.parser

import com.intellij.lang.DefaultASTFactoryImpl
import com.intellij.psi.impl.source.tree.{LazyParseableElement, LeafElement}
import com.intellij.psi.tree.{IElementType, ILazyParseableElementType}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScBlockExprImpl
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocElementType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
import org.jetbrains.plugins.scala.lang.scaladoc.psi.impl.{ScDocCommentImpl, ScDocReferenceLinkImpl, ScPsiDocTokenImpl}
import org.jetbrains.plugins.scalaDirective.lang.lexer.ScalaDirectiveElementType
import org.jetbrains.plugins.scalaDirective.lang.parser.ScalaDirectiveElementTypes
import org.jetbrains.plugins.scalaDirective.psi.impl.{ScDirectiveImpl, ScDirectiveTokenImpl}

/**
 * @note extending default cause it was the factory that was used by IDEA
 * @see [[com.intellij.psi.impl.source.tree.JavaASTFactory]]
 */
final class ScalaASTFactory extends DefaultASTFactoryImpl {

  override def createLazy(elementType: ILazyParseableElementType, text: CharSequence): LazyParseableElement = elementType match {
    case ScCodeBlockElementType.BlockExpression =>
      new ScBlockExprImpl(elementType, text)
    case ScalaDocElementTypes.SCALA_DOC_COMMENT =>
      new ScDocCommentImpl(text, elementType)
    case ScalaDocElementTypes.SCALA_DOC_REFERENCE_LINK =>
      new ScDocReferenceLinkImpl(elementType, text)
    case ScalaDirectiveElementTypes.SCALA_DIRECTIVE =>
      new ScDirectiveImpl(text, elementType)
    case _ =>
      super.createLazy(elementType, text)
  }

  override def createLeaf(typ: IElementType, text: CharSequence): LeafElement =
    typ match {
      case cliElement: ScalaDirectiveElementType =>
        new ScDirectiveTokenImpl(cliElement, text)
      case scDoc: ScalaDocElementType =>
        new ScPsiDocTokenImpl(scDoc, text)
      case _ =>
        super.createLeaf(typ, text)
    }
}
