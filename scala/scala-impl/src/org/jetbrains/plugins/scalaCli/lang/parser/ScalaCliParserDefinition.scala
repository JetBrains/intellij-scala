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
import org.jetbrains.plugins.scala.lang.scalacli.lexer.ScalaCliTokenTypes._
import org.jetbrains.plugins.scala.lang.scalacli.parser.ScalaCliElementTypes
import org.jetbrains.plugins.scala.lang.scalacli.psi.impl.inner.{ScCliDirectiveCommandImpl, ScCliDirectiveKeyImpl, ScCliDirectiveValueImpl}
import org.jetbrains.plugins.scalaCli.ScalaCliLanguage

class ScalaCliParserDefinition extends ParserDefinition {

  import ScalaCliElementTypes._

  override val getFileNodeType = new IFileElementType(ScalaCliLanguage.INSTANCE)

  //noinspection TypeAnnotation
  override val getCommentTokens = TokenSet.create(SCALA_CLI_DIRECTIVE)

  //noinspection TypeAnnotation
  override val getStringLiteralElements = TokenSet.create()

  override def createLexer(project: Project) =
    new FlexAdapter(new ScalaCliLexer(null.asInstanceOf[java.io.Reader]))

  override def createParser(project: Project) =
    new ScalaCliParser

  override def createElement(node: ASTNode): PsiElement = node.getElementType match {
    case `tCLI_DIRECTIVE_COMMAND` =>
      new ScCliDirectiveCommandImpl(node)
    case `tCLI_DIRECTIVE_KEY` => new ScCliDirectiveKeyImpl(node)
    case `tCLI_DIRECTIVE_VALUE` => new ScCliDirectiveValueImpl(node)
    case _ =>
      new ASTWrapperPsiElement(node)
  }

  override def createFile(viewProvider: FileViewProvider): PsiFile = null
}
