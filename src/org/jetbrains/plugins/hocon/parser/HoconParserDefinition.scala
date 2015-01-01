package org.jetbrains.plugins.hocon.parser

import com.intellij.lang.ParserDefinition.SpaceRequirements
import com.intellij.lang.{ASTNode, ParserDefinition, PsiParser}
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.{IFileElementType, TokenSet}
import com.intellij.psi.{FileViewProvider, PsiElement, PsiFile}
import org.jetbrains.plugins.hocon.lexer.{HoconLexer, HoconTokenSets}
import org.jetbrains.plugins.hocon.psi.{HoconPsiCreator, HoconPsiFile}

class HoconParserDefinition extends ParserDefinition {

  import org.jetbrains.plugins.hocon.lexer.HoconTokenType._

  def spaceExistanceTypeBetweenTokens(left: ASTNode, right: ASTNode): SpaceRequirements =
    (left.getElementType, right.getElementType) match {
      case (Dollar, SubLBrace) | (SubLBrace, QMark) => SpaceRequirements.MUST_NOT
      case _ => SpaceRequirements.MAY
    }

  def createFile(viewProvider: FileViewProvider): PsiFile =
    new HoconPsiFile(viewProvider)

  def createElement(node: ASTNode): PsiElement =
    HoconPsiCreator.createElement(node)

  def getStringLiteralElements: TokenSet =
    HoconTokenSets.StringLiteral

  def getCommentTokens: TokenSet =
    HoconTokenSets.Comment

  def getWhitespaceTokens: TokenSet =
    HoconTokenSets.Whitespace

  def getFileNodeType: IFileElementType =
    HoconElementType.HoconFileElementType

  def createParser(project: Project): PsiParser =
    new HoconPsiParser

  def createLexer(project: Project): Lexer =
    new HoconLexer
}
