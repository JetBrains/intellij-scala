package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.{ASTNode, ParserDefinition}
import com.intellij.openapi.project.Project
import com.intellij.psi.{FileViewProvider, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

//noinspection TypeAnnotation
abstract class ScalaParserDefinitionBase protected() extends ParserDefinition {

  override def createElement(node: ASTNode): PsiElement = ASTNodeToPsiElement.map(node)

  override def createFile(viewProvider: FileViewProvider): ScalaFile

  import lexer.ScalaTokenTypes._

  override def getCommentTokens = COMMENTS_TOKEN_SET

  override def getStringLiteralElements = STRING_LITERAL_TOKEN_SET

  override def getWhitespaceTokens = WHITES_SPACES_TOKEN_SET

  override def spaceExistenceTypeBetweenTokens(leftNode: ASTNode, rightNode: ASTNode): ParserDefinition.SpaceRequirements = {
    val isNeighbour = ScalaPsiUtil.getParentImportStatement(leftNode.getPsi) match {
      case null => false
      case importStatement => importStatement.getTextRange.getEndOffset == rightNode.getTextRange.getStartOffset
    }

    import ParserDefinition.SpaceRequirements._
    rightNode.getElementType match {
      case `tWHITE_SPACE_IN_LINE` if rightNode.textContains('\n') => MAY
      case _ if isNeighbour => MUST_LINE_BREAK
      case `kIMPORT` => MUST_LINE_BREAK
      case _ => MAY
    }
  }
}
