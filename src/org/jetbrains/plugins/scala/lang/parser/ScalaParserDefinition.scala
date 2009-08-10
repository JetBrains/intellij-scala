package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.lang.ParserDefinition, com.intellij.lang.PsiParser
import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import psi.impl.ScalaFileImpl

/**
 * @author ilyas
 */
class ScalaParserDefinition extends ScalaParserDefinitionWrapper{

  def createLexer(project: Project) = new ScalaLexer

  def createLexer = new ScalaLexer

  def createParser(project: Project): PsiParser = new ScalaParser

  def getFileNodeType: IFileElementType = ScalaElementTypes.FILE

  def getCommentTokens: TokenSet = ScalaTokenTypes.COMMENTS_TOKEN_SET

  def getStringLiteralElements: TokenSet = ScalaTokenTypes.STRING_LITERAL_TOKEN_SET

  def getWhitespaceTokens: TokenSet = ScalaTokenTypes.WHITES_SPACES_TOKEN_SET

  def createElement(astNode: ASTNode): PsiElement = ScalaPsiCreator.createElement(astNode)

  def createFile(fileViewProvider: FileViewProvider): PsiFile = {
    return new ScalaFileImpl(fileViewProvider);
  }

  override def spaceExistanceTypeBetweenTokens(leftNode: ASTNode, rightNode: ASTNode): ParserDefinition.SpaceRequirements = {
    import ParserDefinition._
    (leftNode.getElementType, rightNode.getElementType) match {
      case (ScalaTokenTypes.tLINE_TERMINATOR, _) => SpaceRequirements.MAY
      case (_, ScalaTokenTypes.kIMPORT) => SpaceRequirements.MUST_LINE_BREAK
      case _ => super.spaceExistanceTypeBetweenTokens(leftNode, rightNode)
    }
  }

}
