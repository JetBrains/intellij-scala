package org.jetbrains.plugins.scala
package lang
package parser

import _root_.com.intellij.psi.util.PsiTreeUtil
import _root_.org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import com.intellij.lang.ParserDefinition, com.intellij.lang.PsiParser
import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import psi.impl.ScalaFileImpl
import formatting.settings.ScalaCodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager

/**
 * @author ilyas
 */
class ScalaParserDefinition extends ScalaParserDefinitionWrapper{

  def createLexer(project: Project) = {
    val treatDocCommentAsBlockComment = CodeStyleSettingsManager.getSettings(project).getCustomSettings(classOf[ScalaCodeStyleSettings]).TREAT_DOC_COMMENT_AS_BLOCK_COMMENT;
    new ScalaLexer(treatDocCommentAsBlockComment)
  }

  def createLexer = new ScalaLexer

  def createParser(project: Project): PsiParser = new ScalaParser

  def getFileNodeType: IFileElementType = ScalaElementTypes.FILE

  def getCommentTokens: TokenSet = ScalaTokenTypes.COMMENTS_TOKEN_SET

  def getStringLiteralElements: TokenSet = ScalaTokenTypes.STRING_LITERAL_TOKEN_SET

  def getWhitespaceTokens: TokenSet = ScalaTokenTypes.WHITES_SPACES_TOKEN_SET

  def createElement(astNode: ASTNode): PsiElement = ScalaPsiCreator.createElement(astNode)

  def createFile(fileViewProvider: FileViewProvider): PsiFile = {
    return new ScalaFileImpl(fileViewProvider);
  }

  override def spaceExistanceTypeBetweenTokens(leftNode: ASTNode, rightNode: ASTNode): ParserDefinition.SpaceRequirements = {
    import ParserDefinition._
    if ((rightNode.getElementType != ScalaTokenTypes.tWHITE_SPACE_IN_LINE || !rightNode.getText.contains("\n"))) {
      val imp: ScImportStmt = PsiTreeUtil.getParentOfType(leftNode.getPsi, classOf[ScImportStmt])
      if (imp != null && rightNode.getTextRange.getStartOffset == imp.getTextRange.getEndOffset)
        return SpaceRequirements.MUST_LINE_BREAK
    }
    (leftNode.getElementType, rightNode.getElementType) match {
      case (_, ScalaTokenTypes.kIMPORT) => SpaceRequirements.MUST_LINE_BREAK
      case _ => super.spaceExistanceTypeBetweenTokens(leftNode, rightNode)
    }
  }

}
