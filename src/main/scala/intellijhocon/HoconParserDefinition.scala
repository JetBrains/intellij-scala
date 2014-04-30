package intellijhocon

import com.intellij.lang.{PsiParser, ASTNode, ParserDefinition}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiFile, PsiElement, FileViewProvider}
import com.intellij.lang.ParserDefinition.SpaceRequirements
import com.intellij.lexer.Lexer
import com.intellij.psi.tree.{TokenSet, IFileElementType}
import intellijhocon.psi.{HoconPsiElement, HoconPsiFile}
import intellijhocon.HoconElementType.HoconFileElementType

class HoconParserDefinition extends ParserDefinition {

  import HoconTokenType._

  def spaceExistanceTypeBetweenTokens(left: ASTNode, right: ASTNode): SpaceRequirements =
    SpaceRequirements.MUST

  def createFile(viewProvider: FileViewProvider): PsiFile =
    new HoconPsiFile(viewProvider)

  def createElement(node: ASTNode): PsiElement =
    new HoconPsiElement(node)

  def getStringLiteralElements: TokenSet =
    TokenSet.create(QuotedString, MultilineString)

  def getCommentTokens: TokenSet =
    TokenSet.create(HashComment, DoubleSlashComment)

  def getWhitespaceTokens: TokenSet =
    TokenSet.create(Whitespace)

  def getFileNodeType: IFileElementType =
    HoconFileElementType

  def createParser(project: Project): PsiParser =
    new HoconPsiParser

  def createLexer(project: Project): Lexer =
    new HoconLexer
}
