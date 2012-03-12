package org.jetbrains.plugins.scala
package lang.surroundWith.surrounders.scaladoc

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import lang.scaladoc.lexer.ScalaDocTokenType
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import lang.surroundWith.surrounders.expression.ScalaExpressionSurrounder

/**
 * User: Dmitry Naydanov
 * Date: 3/2/12
 */

class ScalaDocWithBoldSurrounder extends ScalaDocWithSyntaxSurrounder {
  def getTemplateDescription: String = "Bold: ''' '''"

  def getSyntaxTag = "'''"
}
