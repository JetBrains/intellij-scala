package org.jetbrains.plugins.scala.lang.scaladoc.reflinks

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.{ASTNode, ParserDefinition, PsiParser}
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.{IFileElementType, TokenSet}
import com.intellij.psi.{FileViewProvider, PsiElement, PsiFile}
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.lang.scaladoc.reflinks.lexer.ScalaDocRefLinkLexer
import org.jetbrains.plugins.scala.lang.scaladoc.reflinks.parser.ScalaDocRefLinkParser
import org.jetbrains.plugins.scala.lang.scaladoc.reflinks.psi.ScalaDocRefLinkFile
import org.jetbrains.plugins.scala.lang.scaladoc.reflinks.psi.impl.{ScDocRefQuerySegmentImpl, ScDocRefStrictMemberIdQueryImpl, ScDocRefThisQueryImpl, ScPackageQueryImpl}

class ScalaDocRefLinkParserDefinition extends ParserDefinition {
  override def createLexer(project: Project): Lexer = new ScalaDocRefLinkLexer

  override def createParser(project: Project): PsiParser = new ScalaDocRefLinkParser

  override def getFileNodeType: IFileElementType = ScalaDocRefLinkParserDefinition.FileNodeType

  @NotNull
  override def getCommentTokens: TokenSet = TokenSet.EMPTY

  @NotNull
  override def getStringLiteralElements: TokenSet = TokenSet.EMPTY

  @NotNull
  override def createElement(node: ASTNode): PsiElement =
    node.getElementType match {
      case ScalaDocRefLinkElementTypes.STRICT_MEMBER_ID => new ScDocRefStrictMemberIdQueryImpl(node)
      case ScalaDocRefLinkElementTypes.QUERY_SEGMENT => new ScDocRefQuerySegmentImpl(node)
      case ScalaDocRefLinkElementTypes.THIS_QUERY_SEGMENT => new ScDocRefThisQueryImpl(node)
      case ScalaDocRefLinkElementTypes.THIS_PACKAGE_SEGMENT => new ScPackageQueryImpl(node)
      case _ => new ASTWrapperPsiElement(node)
    }

  override def createFile(viewProvider: FileViewProvider): PsiFile = new ScalaDocRefLinkFile(viewProvider)
}

object ScalaDocRefLinkParserDefinition {
  val FileNodeType: IFileElementType = new IFileElementType(ScalaDocRefLinkLanguage.INSTANCE)
}
