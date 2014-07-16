package intellijhocon
package parser

import com.intellij.lang.{PsiParser, ASTNode, ParserDefinition}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiFile, PsiElement, FileViewProvider}
import com.intellij.lang.ParserDefinition.SpaceRequirements
import com.intellij.lexer.Lexer
import com.intellij.psi.tree.{TokenSet, IFileElementType}
import intellijhocon.psi.{HoconPsiElement, HoconPsiFile}
import intellijhocon.lexer.{HoconTokenType, HoconTokenSets, HoconLexer}

class HoconParserDefinition extends ParserDefinition {

  import HoconTokenType._

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
