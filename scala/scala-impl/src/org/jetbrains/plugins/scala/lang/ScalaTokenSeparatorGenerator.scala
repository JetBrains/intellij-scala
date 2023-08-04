package org.jetbrains.plugins.scala.lang

import com.intellij.application.options.CodeStyle
import com.intellij.lang.{ASTNode, TokenSeparatorGenerator}
import com.intellij.psi.TokenType
import com.intellij.psi.impl.source.tree.Factory
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScImportStmtElementType
import org.jetbrains.plugins.scalaDirective.lang.lexer.ScalaDirectiveElementType

/**
 * Here we handle cases where 2 adjacent elements of different languages require a newline or other whitespace separator.
 *
 * Note that cases of same-language adjacent elements are handled by [[com.intellij.lang.LanguageTokenSeparatorGenerators]]
 */
class ScalaTokenSeparatorGenerator extends TokenSeparatorGenerator {

  override def generateWhitespaceBetweenTokens(left: ASTNode, right: ASTNode): ASTNode = {

    if (left.getElementType.is[ScalaDirectiveElementType] && right.getTreeParent.getElementType.is[ScImportStmtElementType]) {

      val leftPsi = left.getPsi
      val project = leftPsi.getProject
      val commonCodeStyleSettings = CodeStyle.getSettings(project).getCommonSettings(ScalaLanguage.INSTANCE)
      val repeatCount = commonCodeStyleSettings.BLANK_LINES_BEFORE_IMPORTS + 1
      val psiManager = leftPsi.getManager

      Factory.createSingleLeafElement(TokenType.WHITE_SPACE, "\n".repeat(repeatCount), 0, repeatCount, null, psiManager)
    } else {
      null
    }
  }
}
