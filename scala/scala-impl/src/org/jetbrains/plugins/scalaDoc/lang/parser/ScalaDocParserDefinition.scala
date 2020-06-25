package org.jetbrains.plugins.scalaDoc
package lang
package parser

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.{ASTNode, ParserDefinition}
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.{IFileElementType, TokenSet}
import com.intellij.psi.{FileViewProvider, PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScalaDocSyntaxElementType
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.{ScalaDocLexer, ScalaDocTokenType}
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocInlinedTag, ScDocTag}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.impl._
import org.jetbrains.plugins.scalaDoc.lang.parser.ScalaDocParserDefinition.isInsideJavaLinkTag

final class ScalaDocParserDefinition extends ParserDefinition {

  import ScalaDocElementTypes._
  import ScalaDocParserDefinition.isInsideThrowsTag
  import ScalaDocTokenType._

  override val getFileNodeType = new IFileElementType(ScalaDocLanguage.INSTANCE)

  //noinspection TypeAnnotation
  override val getCommentTokens = TokenSet.create(SCALA_DOC_COMMENT)

  //noinspection TypeAnnotation
  override val getStringLiteralElements = TokenSet.create()

  override def createLexer(project: Project) = new ScalaDocLexer

  override def createParser(project: Project) = new ScalaDocParser

  /**
   * see also [[org.jetbrains.plugins.scala.lang.parser.ScalaASTFactory.createLeaf]]
   * @inheritdoc
   */
  override def createElement(node: ASTNode): PsiElement = node.getElementType match {
    case DOC_INNER_CODE_TAG   => new ScDocInnerCodeElementImpl(node)
    case DOC_INLINED_TAG      => new ScDocInlinedTagImpl(node)
    case DOC_PARAM_REF        => new ScDocParamRefImpl(node)
    case DOC_METHOD_REF       => new ScDocMethodRefImpl(node)
    case DOC_FIELD_REF        => new ScDocFieldRefImpl(node)
    case DOC_METHOD_PARAMS    => new ScDocMethodParamsImpl(node)
    case DOC_METHOD_PARAMETER => new ScDocMethodParameterImpl(node)
    case DOC_CODE_LINK_VALUE  => new ScDocResolvableCodeReferenceImpl(node)
    case DOC_TAG              => new ScDocTagImpl(node)
    case DOC_TAG_VALUE_TOKEN                   =>
      if (isInsideThrowsTag(node)) new ScDocThrowTagValueImpl(node)
      else if (isInsideJavaLinkTag(node)) new ScDocResolvableCodeReferenceImpl(node)
      else new ScDocTagValueImpl(node)
    case syntaxType: ScalaDocSyntaxElementType =>
      val result = new ScDocSyntaxElementImpl(node)
      result.setFlag(syntaxType.getFlagConst)

      var parentNode = node
      while (parentNode.getTreeParent != null &&
        parentNode.getElementType != SCALA_DOC_COMMENT) {

        parentNode = parentNode.getTreeParent
        parentNode.getElementType match {
          case elementType: ScalaDocSyntaxElementType =>
            result.setFlag(elementType.getFlagConst)
          case _ =>
        }
      }

      result
    case DOC_LIST      => new ScDocListImpl(node)
    case DOC_LIST_ITEM => new ScDocListItemImpl(node)
    case DOC_PARAGRAPH => new ScDocParagraphImpl(node)
    case _             => new ASTWrapperPsiElement(node)
  }

  override def createFile(viewProvider: FileViewProvider): PsiFile = null
}

object ScalaDocParserDefinition {

  private def isInsideThrowsTag(node: ASTNode): Boolean = {
    var parent = node.getTreeParent

    while (parent != null && parent.getPsi != null && !parent.getPsi.is[ScDocTag, ScDocComment])
      parent = parent.getTreeParent

    parent != null && (parent.getPsi match {
      case tag: ScDocTag => tag.name == MyScaladocParsing.THROWS_TAG
      case _             => false
    })
  }

  private def isInsideJavaLinkTag(node: ASTNode): Boolean = {
    var parent = node.getTreeParent

    while (parent != null && parent.getPsi != null && !parent.getPsi.is[ScDocInlinedTag, ScDocComment] )
      parent = parent.getTreeParent

    parent != null && (parent.getPsi match {
      case inlineTag: ScDocInlinedTag =>
        val name = "@" + inlineTag.name
        name == MyScaladocParsing.JAVA_LINK_TAG || name == MyScaladocParsing.JAVA_LINK_PLAIN_TAG
      case _             => false
    })
  }
}
