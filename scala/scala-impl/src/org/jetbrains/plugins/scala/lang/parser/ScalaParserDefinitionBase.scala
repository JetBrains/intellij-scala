package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.{ASTNode, ParserDefinition}
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{FileViewProvider, PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubFileElementType
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings.{getInstance => ScalaProjectSettings}

//noinspection TypeAnnotation
abstract class ScalaParserDefinitionBase(override val getFileNodeType: ScStubFileElementType) extends ParserDefinition {

  override def createLexer(project: Project) = {
    val treatDocCommentAsBlockComment = ScalaProjectSettings(project).isTreatDocCommentAsBlockComment
    new lexer.ScalaLexer(treatDocCommentAsBlockComment)
  }

  override def createParser(project: Project) = new ScalaParser

  override def createElement(node: ASTNode): PsiElement = node.getElementType match {
    case creator: SelfPsiCreator => creator.createElement(node)
    case elementType: scaladoc.lexer.ScalaDocElementType => scaladoc.psi.ScalaDocPsiCreator.createElement(node, elementType)
    case _ => new ASTWrapperPsiElement(node)
  }

  override def createFile(viewProvider: FileViewProvider): PsiFile =
    ScalaFileFactory.EP_NAME.getExtensions.view
      .flatMap(_.createFile(viewProvider))
      .headOption
      .getOrElse(new ScalaFileImpl(viewProvider))

  import lexer.ScalaTokenTypes.{COMMENTS_TOKEN_SET, STRING_LITERAL_TOKEN_SET, WHITES_SPACES_TOKEN_SET, kIMPORT => Import, tWHITE_SPACE_IN_LINE => WS}

  override def getCommentTokens: TokenSet = COMMENTS_TOKEN_SET

  override def getStringLiteralElements = STRING_LITERAL_TOKEN_SET

  override def getWhitespaceTokens = WHITES_SPACES_TOKEN_SET

  override def spaceExistenceTypeBetweenTokens(leftNode: ASTNode, rightNode: ASTNode): ParserDefinition.SpaceRequirements = {
    val isNeighbour = PsiTreeUtil.getParentOfType(leftNode.getPsi, classOf[ScImportStmt]) match {
      case null => false
      case importStatement => importStatement.getTextRange.getEndOffset == rightNode.getTextRange.getStartOffset
    }

    import ParserDefinition.SpaceRequirements._
    rightNode.getElementType match {
      case WS if rightNode.getText.contains('\n') => MAY
      case _ if isNeighbour => MUST_LINE_BREAK
      case Import => MUST_LINE_BREAK
      case _ => MAY
    }
  }
}
