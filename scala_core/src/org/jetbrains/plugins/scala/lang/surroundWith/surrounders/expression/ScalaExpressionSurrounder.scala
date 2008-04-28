package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

/**
 * User: Dmitry.Krasilschikov
 * Date: 09.01.2007
 *
 */

import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.util.DebugPrint
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Expr
import com.intellij.psi.PsiWhiteSpace;


import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/*
 * Surrounds an expression and return an expression
 */

abstract class ScalaExpressionSurrounder extends ScalaSurrounderByExpression {
  override def isApplicable(element : PsiElement) : Boolean = {
    element match {
      case _ : ScExpression | _: PsiWhiteSpace => true
      case e => {
        e.getNode.getElementType == ScalaTokenTypes.tLINE_TERMINATOR
      }
    }
  }
}