package org.jetbrains.plugins.hocon.parser

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ParserDefinition.SpaceRequirements
import com.intellij.lang.{ASTNode, ParserDefinition, PsiParser}
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.{IFileElementType, TokenSet}
import com.intellij.psi.{FileViewProvider, PsiElement, PsiFile}
import org.jetbrains.plugins.hocon.lexer.{HoconLexer, HoconTokenSets, HoconTokenType}
import org.jetbrains.plugins.hocon.psi._

class HoconParserDefinition extends ParserDefinition {

  import HoconTokenSets._
  import HoconTokenType._

  override def spaceExistanceTypeBetweenTokens(left: ASTNode, right: ASTNode): SpaceRequirements =
    (left.getElementType, right.getElementType) match {
      case (Dollar, SubLBrace) | (SubLBrace, QMark) => SpaceRequirements.MUST_NOT
      case _ => SpaceRequirements.MAY
    }

  def createFile(viewProvider: FileViewProvider): PsiFile =
    new HoconPsiFile(viewProvider)

  def createElement(node: ASTNode): PsiElement =
    HoconParserDefinition.createElement(node)

  def getStringLiteralElements: TokenSet = StringLiteral

  def getCommentTokens: TokenSet = Comment

  override def getWhitespaceTokens: TokenSet = Whitespace

  def getFileNodeType: IFileElementType =
    HoconElementType.HoconFileElementType

  def createParser(project: Project): PsiParser =
    new HoconPsiParser

  def createLexer(project: Project): Lexer =
    new HoconLexer
}

object HoconParserDefinition {

  import HoconElementType._

  private def createElement(ast: ASTNode): ASTWrapperPsiElement = ast.getElementType match {
    case Object => new HObject(ast)
    case ObjectEntries => new HObjectEntries(ast)
    case Include => new HInclude(ast)
    case Included => new HIncluded(ast)
    case ObjectField => new HObjectField(ast)
    case PrefixedField => new HPrefixedField(ast)
    case ValuedField => new HValuedField(ast)
    case Path => new HPath(ast)
    case Key => new HKey(ast)
    case Array => new HArray(ast)
    case Substitution => new HSubstitution(ast)
    case Concatenation => new HConcatenation(ast)
    case UnquotedString => new HUnquotedString(ast)
    case StringValue => new HStringValue(ast)
    case KeyPart => new HKeyPart(ast)
    case IncludeTarget => new HIncludeTarget(ast)
    case Number => new HNumber(ast)
    case Null => new HNull(ast)
    case Boolean => new HBoolean(ast)
    case _ => new ASTWrapperPsiElement(ast)
  }
}