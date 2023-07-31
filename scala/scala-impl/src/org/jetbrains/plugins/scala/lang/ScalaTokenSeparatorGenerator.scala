package org.jetbrains.plugins.scala.lang

import com.intellij.application.options.CodeStyle
import com.intellij.lang.{ASTNode, TokenSeparatorGenerator}
import com.intellij.psi.TokenType
import com.intellij.psi.impl.source.tree.Factory
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScImportStmtElementType
import org.jetbrains.plugins.scalaDirective.lang.lexer.ScalaDirectiveElementType

class ScalaTokenSeparatorGenerator extends TokenSeparatorGenerator {

  override def generateWhitespaceBetweenTokens(left: ASTNode, right: ASTNode): ASTNode = {

    if (left.getElementType.is[ScalaDirectiveElementType] && right.getTreeParent.getElementType.is[ScImportStmtElementType]) {

      val manager = right.getTreeParent.getPsi.getManager
      val commonCodeStyleSettings = CodeStyle.getSettings(right.getPsi.getProject).getCommonSettings(ScalaLanguage.INSTANCE)
      val repeatCount = commonCodeStyleSettings.BLANK_LINES_BEFORE_IMPORTS + 1

      Factory.createSingleLeafElement(TokenType.WHITE_SPACE, "\n".repeat(repeatCount), 0, repeatCount, null, manager)
    } else {
      null
    }
  }
}
