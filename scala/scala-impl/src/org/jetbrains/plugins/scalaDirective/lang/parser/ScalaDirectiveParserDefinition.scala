package org.jetbrains.plugins.scalaDirective
package lang
package parser

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.{ASTNode, ParserDefinition}
import com.intellij.lexer.FlexAdapter
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.{IFileElementType, TokenSet}
import com.intellij.psi.{FileViewProvider, PsiElement, PsiFile}
import org.jetbrains.plugins.scalaDirective.lang.lexer._ScalaDirectiveLexer

class ScalaDirectiveParserDefinition extends ParserDefinition {

  override val getFileNodeType = new IFileElementType(ScalaDirectiveLanguage.INSTANCE)

  //noinspection TypeAnnotation
  override val getCommentTokens = TokenSet.create()

  //noinspection TypeAnnotation
  override val getStringLiteralElements = TokenSet.create()

  override def createLexer(project: Project) =
    new FlexAdapter(new _ScalaDirectiveLexer(null.asInstanceOf[java.io.Reader]))

  override def createParser(project: Project) = new ScalaDirectiveParser

  override def createElement(node: ASTNode): PsiElement = new ASTWrapperPsiElement(node)

  override def createFile(viewProvider: FileViewProvider): PsiFile = null
}
