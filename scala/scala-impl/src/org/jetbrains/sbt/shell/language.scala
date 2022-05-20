package org.jetbrains.sbt
package shell

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang._
import com.intellij.lexer.FlexAdapter
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.{IElementType, IFileElementType, TokenSet}
import com.intellij.psi.{FileViewProvider, PsiElement, TokenType}
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.LanguageFileTypeBase

import javax.swing.Icon

object SbtShellLanguage extends Language("sbtShell") with DependentLanguage {
  override def isCaseSensitive: Boolean = true
}

final class SbtShellTokenType(@NotNull @NonNls debugName: String) extends IElementType(debugName, SbtShellLanguage)

final class SbtShellElementType(@NotNull @NonNls debugName: String) extends IElementType(debugName, SbtShellLanguage)

final class SbtShellParserDefinition extends ParserDefinition {

  import SbtShellParserDefinition._

  override def createLexer(project: Project): FlexAdapter =
    new SbtShellLexerAdapter

  override def createParser(project: Project): PsiParser =
    new grammar.SbtShellParser

  override def createFile(viewProvider: FileViewProvider): PsiFileBase =
    new SbtShellFile(viewProvider, SbtShellFileType)

  override def createElement(node: ASTNode): PsiElement =
    grammar.SbtShellTypes.Factory.createElement(node)

  override def getFileNodeType: IFileElementType = File

  override def getWhitespaceTokens: TokenSet = WhiteSpaces

  override def getCommentTokens: TokenSet = TokenSet.EMPTY

  override def getStringLiteralElements: TokenSet = TokenSet.EMPTY
}

object SbtShellParserDefinition {

  private val WhiteSpaces = TokenSet.create(TokenType.WHITE_SPACE)
  private val File = new IFileElementType("sbt shell file", SbtShellLanguage)

  /**
   * Dummy file type required by the sbt shell LightVirtualFile
   */
  object SbtShellFileType extends LanguageFileTypeBase(SbtShellLanguage) {

    override def getDefaultExtension = "sbts"

    override def getIcon: Icon = language.SbtFileType.getIcon
  }

  private final class SbtShellLexerAdapter extends FlexAdapter(new grammar._SbtShellLexer)

  private final class SbtShellFile(viewProvider: FileViewProvider,
                                   override val getFileType: LanguageFileTypeBase)
    extends PsiFileBase(viewProvider, SbtShellLanguage)
}