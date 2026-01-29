package org.jetbrains.plugins.scala.lang.scaladoc.reflinks

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.{ASTNode, ParserDefinition, PsiParser}
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.{IFileElementType, TokenSet}
import com.intellij.psi.{FileViewProvider, PsiElement, PsiFile}
import org.jetbrains.annotations.NotNull

class ScalaDocRefLinkParserDefinition extends ParserDefinition {
  override def createLexer(project: Project): Lexer = new ScalaDocRefLinkLexer

  override def createParser(project: Project): PsiParser = ???

  override def getFileNodeType: IFileElementType = ScalaDocRefLinkParserDefinition.FileNodeType

  @NotNull
  override def getCommentTokens: TokenSet = TokenSet.EMPTY

  @NotNull
  override def getStringLiteralElements: TokenSet = TokenSet.EMPTY

  @NotNull
  override def createElement(node: ASTNode): PsiElement = new ASTWrapperPsiElement(node)

  override def createFile(viewProvider: FileViewProvider): PsiFile = ???
}

object ScalaDocRefLinkParserDefinition {
  val FileNodeType: IFileElementType = new IFileElementType(ScalaDocRefLinkLanguage.INSTANCE)
}
