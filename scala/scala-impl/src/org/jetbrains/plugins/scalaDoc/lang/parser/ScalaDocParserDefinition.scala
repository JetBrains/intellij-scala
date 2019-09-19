package org.jetbrains.plugins.scalaDoc
package lang
package parser

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.{ASTNode, ParserDefinition}
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.{IFileElementType, TokenSet}
import com.intellij.psi.{FileViewProvider, PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScaladocSyntaxElementType
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.{ScalaDocLexer, ScalaDocTokenType}
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocTag
import org.jetbrains.plugins.scala.lang.scaladoc.psi.impl._

final class ScalaDocParserDefinition extends ParserDefinition {

  import ScalaDocElementTypes._
  import ScalaDocTokenType._

  override val getFileNodeType = new IFileElementType(ScalaDocLanguage.INSTANCE)

  //noinspection TypeAnnotation
  override val getCommentTokens = TokenSet.create(SCALA_DOC_COMMENT)

  //noinspection TypeAnnotation
  override val getStringLiteralElements = TokenSet.create()

  override def createLexer(project: Project) = new ScalaDocLexer

  override def createParser(project: Project) = new ScalaDocParser

  override def createElement(node: ASTNode): PsiElement = node.getElementType match {
    case elementType: ScaladocSyntaxElementType =>
      val result = new ScDocSyntaxElementImpl(node)
      result.setFlag(elementType.getFlagConst)

      var parentNode = node
      while (parentNode.getTreeParent != null &&
        parentNode.getElementType != SCALA_DOC_COMMENT) {

        parentNode = parentNode.getTreeParent
        parentNode.getElementType match {
          case elementType: ScaladocSyntaxElementType =>
            result.setFlag(elementType.getFlagConst)
          case _ =>
        }
      }

      result
    case DOC_INNER_CODE_TAG => new ScDocInnerCodeElementImpl(node)
    case DOC_TAG => new ScDocTagImpl(node)
    case DOC_INLINED_TAG => new ScDocInlinedTagImpl(node)
    case DOC_PARAM_REF => new ScDocParamRefImpl(node)
    case DOC_METHOD_REF => new ScDocMethodRefImpl(node)
    case DOC_FIELD_REF => new ScDocFieldRefImpl(node)
    case DOC_METHOD_PARAMS => new ScDocMethodParamsImpl(node)
    case DOC_METHOD_PARAMETER => new ScDocMethodParameterImpl(node)
    case DOC_CODE_LINK_VALUE => new ScDocResolvableCodeReferenceImpl(node)
    case DOC_TAG_VALUE_TOKEN =>
      var parent = node.getTreeParent

      while (parent != null && parent.getPsi != null && !parent.getPsi.isInstanceOf[ScDocTag]) {
        parent = parent.getTreeParent
      }

      val isThrows = parent != null && (parent.getPsi match {
        case tag: ScDocTag => tag.getName == MyScaladocParsing.THROWS_TAG
        case _ => false
      })

      if (isThrows) new ScDocThrowTagValueImpl(node)
      else new ScDocTagValueImpl(node)
    case _ => new ASTWrapperPsiElement(node)
  }

  override def createFile(viewProvider: FileViewProvider): PsiFile = null
}
