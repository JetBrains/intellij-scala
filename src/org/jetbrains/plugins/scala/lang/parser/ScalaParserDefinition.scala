package org.jetbrains.plugins.scala.lang.parser

import com.intellij.lang.ParserDefinition, com.intellij.lang.PsiParser
import com.intellij.lang.ASTNode
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaFile

/**
 * Author: Ilya Sergey
 * Date: 25.09.2006
 * Time: 14:23:22
 */
class ScalaParserDefinition extends ParserDefinition {

  def createLexer(project: Project) = new ScalaLexer()

  def createLexer() = new ScalaLexer()

  def createParser(project: Project): PsiParser = new ScalaParser()

  def getFileNodeType(): IFileElementType = ScalaElementTypes.FILE

  def getCommentTokens(): TokenSet = ScalaTokenTypes.COMMENTS_TOKEN_SET

  def getStringLiteralElements(): TokenSet = ScalaTokenTypes.STRING_LITERAL_TOKEN_SET

  def getWhitespaceTokens(): TokenSet = ScalaTokenTypes.WHITES_SPACES_TOKEN_SET

  def createElement(astNode: ASTNode): PsiElement = ScalaPsiCreator.createElement(astNode)

  def createFile(fileViewProvider: FileViewProvider): PsiFile = {
    return new ScalaFile(fileViewProvider);
  }

  def spaceExistanceTypeBetweenTokens(astNode: ASTNode, astNode1: ASTNode): ParserDefinition.SpaceRequirements = {
    throw new UnsupportedOperationException("spaceExistanceTypeBetweenTokens not implemented in org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition");
  }

}
