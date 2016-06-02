package org.jetbrains.plugins.scala
package lang
package parser

import _root_.com.intellij.psi.util.PsiTreeUtil
import _root_.org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import com.intellij.lang.{ASTNode, ParserDefinition}
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.{FileViewProvider, PsiFile}
import org.jetbrains.plugins.dotty.lang.parser.{DottyElementTypes, DottyParser, DottyPsiCreator}
import org.jetbrains.plugins.scala.lang.lexer.{ScalaLexer, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.settings._

/**
 * @author ilyas
 */
class ScalaParserDefinition extends ScalaParserDefinitionWrapper {

  private var hasDotty = false

  def createLexer(project: Project) = {
    val treatDocCommentAsBlockComment = ScalaProjectSettings.getInstance(project).isTreatDocCommentAsBlockComment
    new ScalaLexer(treatDocCommentAsBlockComment)
  }

  def createParser(project: Project) = {
    hasDotty = project.hasDotty
    if (hasDotty) new DottyParser else new ScalaParser
  }

  def getFileNodeType = (if (hasDotty) DottyElementTypes else ScalaElementTypes).file

  def getCommentTokens: TokenSet = ScalaTokenTypes.COMMENTS_TOKEN_SET

  def getStringLiteralElements: TokenSet = ScalaTokenTypes.STRING_LITERAL_TOKEN_SET

  def getWhitespaceTokens: TokenSet = ScalaTokenTypes.WHITES_SPACES_TOKEN_SET

  def createElement(astNode: ASTNode) = (if (hasDotty) DottyPsiCreator else ScalaPsiCreator).createElement(astNode)

  def createFile(fileViewProvider: FileViewProvider): PsiFile = {
    ScalaFileFactory.EP_NAME.getExtensions
            .view
            .flatMap(_.createFile(fileViewProvider))
            .headOption
            .getOrElse(new ScalaFileImpl(fileViewProvider))
  }

  override def spaceExistanceTypeBetweenTokens(leftNode: ASTNode, rightNode: ASTNode): ParserDefinition.SpaceRequirements = {
    import com.intellij.lang.ParserDefinition._
    if (rightNode.getElementType != ScalaTokenTypes.tWHITE_SPACE_IN_LINE || !rightNode.getText.contains("\n")) {
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
