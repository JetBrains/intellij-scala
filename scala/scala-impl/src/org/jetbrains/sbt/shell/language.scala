package org.jetbrains.sbt
package shell

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ParserDefinition.SpaceRequirements
import com.intellij.lang.{ASTNode, Language, ParserDefinition, PsiParser}
import com.intellij.lexer.{FlexAdapter, Lexer}
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.{IElementType, IFileElementType, TokenSet}
import com.intellij.psi.{FileViewProvider, PsiElement, PsiFile, TokenType}
import javax.swing.Icon
import org.jetbrains.annotations._

object SbtShellLanguage extends Language("sbtShell") {
  override def isCaseSensitive: Boolean = true
}

class SbtShellTokenType(@NotNull @NonNls debugName: String) extends IElementType(debugName, SbtShellLanguage)

class SbtShellElementType(@NotNull @NonNls debugName: String) extends IElementType(debugName, SbtShellLanguage)

/**
  * Dummy file type required by the sbt shell LightVirtualFile
  */
object SbtShellFileType extends LanguageFileType(SbtShellLanguage) {
  override def getDefaultExtension = "sbts"

  override def getName = "sbtShell"

  override def getIcon: Icon = language.SbtFileType.getIcon

  override def getDescription = "sbt shell file dummy"
}

class SbtShellLexerAdapter extends FlexAdapter(new grammar._SbtShellLexer)

class SbtShellFile(viewProvider: FileViewProvider) extends PsiFileBase(viewProvider, SbtShellLanguage) {
  override def getFileType: SbtShellFileType.type = SbtShellFileType
  override def toString: String = "sbt shell file"
}

class SbtShellParserDefinition extends ParserDefinition {
  import SbtShellParserDefinition._
  override def getWhitespaceTokens: TokenSet = WHITE_SPACES

  override def spaceExistenceTypeBetweenTokens(left: ASTNode, right: ASTNode): SpaceRequirements = SpaceRequirements.MAY
  override def createFile(viewProvider: FileViewProvider): PsiFile = new SbtShellFile(viewProvider)
  override def getCommentTokens: TokenSet = TokenSet.EMPTY

  override def createElement(node: ASTNode): PsiElement = grammar.SbtShellTypes.Factory.createElement(node)
  override def getStringLiteralElements: TokenSet = TokenSet.EMPTY
  override def createLexer(project: Project): Lexer = new SbtShellLexerAdapter
  override def getFileNodeType: IFileElementType = FILE

  override def createParser(project: Project): PsiParser = new grammar.SbtShellParser
}

object SbtShellParserDefinition {
  val WHITE_SPACES: TokenSet = TokenSet.create(TokenType.WHITE_SPACE)
  val FILE = new IFileElementType(SbtShellLanguage)
}