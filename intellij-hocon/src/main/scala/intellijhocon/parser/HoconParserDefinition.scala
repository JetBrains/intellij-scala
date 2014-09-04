package intellijhocon
package parser

import com.intellij.lang.ParserDefinition.SpaceRequirements
import com.intellij.lang.{ASTNode, ParserDefinition, PsiParser}
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.{IFileElementType, TokenSet}
import com.intellij.psi.{FileViewProvider, PsiElement, PsiFile}
import intellijhocon.lexer.{HoconLexer, HoconTokenSets, HoconTokenType}
import intellijhocon.psi.{HoconPsiElement, HoconPsiFile}

class HoconParserDefinition extends ParserDefinition {

  import intellijhocon.lexer.HoconTokenType._

  def spaceExistanceTypeBetweenTokens(left: ASTNode, right: ASTNode): SpaceRequirements =
    (left.getElementType, right.getElementType) match {
      case (Dollar, SubLBrace) | (SubLBrace, QMark) => SpaceRequirements.MUST_NOT
      case _ => SpaceRequirements.MAY
    }

  def createFile(viewProvider: FileViewProvider): PsiFile =
    new HoconPsiFile(viewProvider)

  def createElement(node: ASTNode): PsiElement =
    new HoconPsiElement(node)

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
