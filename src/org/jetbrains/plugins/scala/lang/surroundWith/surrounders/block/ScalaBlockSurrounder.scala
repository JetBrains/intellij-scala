package org.jetbrains.plugins.scala.lang.surroundWith.surrounders;

/**
 * @autor: Dmitry.Krasilschikov
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

import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

/*
 * Surrounds a block and return an expression
 */

abstract class ScalaBlockSurrounder extends ScalaSurrounderByExpression {
   override def isApplicable(element : PsiElement) : Boolean = {
    element match {
      case _ : ScBlock => true
      case _ => false
    }
  }
}
