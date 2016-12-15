package org.jetbrains.sbt.shell

import javax.swing.Icon

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ParserDefinition.SpaceRequirements
import com.intellij.lang.{ASTNode, Language, ParserDefinition, PsiParser}
import com.intellij.lexer.{FlexAdapter, Lexer}
import com.intellij.openapi.fileTypes.{FileTypeConsumer, FileTypeFactory, LanguageFileType}
import com.intellij.openapi.project.Project
import com.intellij.psi.{FileViewProvider, PsiElement, PsiFile, TokenType}
import com.intellij.psi.tree.{IElementType, IFileElementType, TokenSet}
import org.jetbrains.annotations._
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.shell.grammar.{SbtShellParser, SbtShellTypes, _SbtShellLexer}

object SbtShellLanguage extends Language("sbtShell") {
  override def isCaseSensitive: Boolean = true
}

class SbtShellTokenType(@NotNull @NonNls debugName: String) extends IElementType(debugName, SbtShellLanguage) {
  override def toString: String = "SbtShellTokenType." + super.toString
}

class SbtShellElementType(@NotNull @NonNls debugName: String) extends IElementType(debugName, SbtShellLanguage)

/**
  * Dummy file type required by the sbt shell LightVirtualFile
  */
object SbtShellFileType extends LanguageFileType(SbtShellLanguage) {
  override def getDefaultExtension: String = "sbts"
  override def getName: String = "sbtShell"
  override def getIcon: Icon = Sbt.FileIcon
  override def getDescription: String = "Sbt Shell file dummy"
}

class SbtShellFileTypeFactory extends FileTypeFactory {
  override def createFileTypes(consumer: FileTypeConsumer): Unit =
    consumer.consume(SbtShellFileType, SbtShellFileType.getDefaultExtension)
}

class SbtShellLexerAdapter extends FlexAdapter(new _SbtShellLexer)

class SbtShellFile(viewProvider: FileViewProvider) extends PsiFileBase(viewProvider, SbtShellLanguage) {
  override def getFileType = SbtShellFileType
  override def toString: String = "Sbt Shell File"
}

class SbtShellParserDefinition extends ParserDefinition {
  import SbtShellParserDefinition._
  override def getWhitespaceTokens: TokenSet = WHITE_SPACES

  override def spaceExistanceTypeBetweenTokens(left: ASTNode, right: ASTNode): SpaceRequirements = SpaceRequirements.MAY
  override def createFile(viewProvider: FileViewProvider): PsiFile = new SbtShellFile(viewProvider)
  override def getCommentTokens: TokenSet = TokenSet.EMPTY
  override def createElement(node: ASTNode): PsiElement = SbtShellTypes.Factory.createElement(node)
  override def getStringLiteralElements: TokenSet = TokenSet.EMPTY
  override def createLexer(project: Project): Lexer = new SbtShellLexerAdapter
  override def getFileNodeType: IFileElementType = FILE
  override def createParser(project: Project): PsiParser = new SbtShellParser
}

object SbtShellParserDefinition {
  val WHITE_SPACES: TokenSet = TokenSet.create(TokenType.WHITE_SPACE)
  val FILE = new IFileElementType(SbtShellLanguage)
}