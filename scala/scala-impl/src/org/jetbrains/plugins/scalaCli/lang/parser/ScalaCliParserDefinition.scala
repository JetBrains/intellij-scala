package org.jetbrains.plugins.scalaCli
package lang
package parser

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.{ASTNode, ParserDefinition}
import com.intellij.lexer.FlexAdapter
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.{IFileElementType, TokenSet}
import com.intellij.psi.{FileViewProvider, PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.scalacli.lexer.ScalaCliLexer
import org.jetbrains.plugins.scalaCli.ScalaCliLanguage

class ScalaCliParserDefinition extends ParserDefinition {

  override val getFileNodeType = new IFileElementType(ScalaCliLanguage.INSTANCE)

  //noinspection TypeAnnotation
  override val getCommentTokens = TokenSet.create()

  //noinspection TypeAnnotation
  override val getStringLiteralElements = TokenSet.create()

  override def createLexer(project: Project) =
    new FlexAdapter(new ScalaCliLexer(null.asInstanceOf[java.io.Reader]))

  override def createParser(project: Project) = new ScalaCliParser

  override def createElement(node: ASTNode): PsiElement = new ASTWrapperPsiElement(node)

  override def createFile(viewProvider: FileViewProvider): PsiFile = null
}
