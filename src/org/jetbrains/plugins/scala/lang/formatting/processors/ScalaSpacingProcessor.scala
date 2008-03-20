package org.jetbrains.plugins.scala.lang.formatting.processors

import com.intellij.formatting._;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;
import org.jetbrains.plugins.scala.lang.psi.ScalaFile;
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._;
import org.jetbrains.plugins.scala.lang.formatting.patterns._

import org.jetbrains.plugins.scala.lang.psi.api.expr._
import com.intellij.formatting.Spacing;

object ScalaSpacingProcessor extends ScalaTokenTypes {


  val NO_SPACING = Spacing.createSpacing(0, 0, 0, false, 0);
  val SINGLE_SPACING = Spacing.createSpacing(1, 1, 0, true, 239);



  def getSpacing(left: ASTNode, right: ASTNode): Spacing = {


    if (right.getElementType.equals(ScalaElementTypes.TYPE_PARAM_CLAUSE) ||
    right.getElementType.equals(ScalaElementTypes.PARAM_CLAUSE) ||
    right.getElementType.equals(ScalaElementTypes.PARAM_CLAUSES) ||
    right.getElementType.equals(ScalaElementTypes.TYPE_ARGS) ||
    right.getElementType.equals(ScalaElementTypes.ARG_EXPRS))
      return NO_SPACING

    if (SpacingTokens.SPACING_BEFORE.contains(right.getElementType))
      return SINGLE_SPACING

    if ((left.getPsi.isInstanceOf[ScInfixExpr] &&
    right.getElementType.equals(ScalaTokenTypes.tIDENTIFIER)) ||
    (right.getPsi.isInstanceOf[ScInfixExpr] &&
    left.getElementType.equals(ScalaTokenTypes.tIDENTIFIER)))
      return SINGLE_SPACING

    if (SpacingTokens.NO_SPACING_BEFORE.contains(right.getElementType))
      return NO_SPACING

    if (SpacingTokens.SPACING_AFTER.contains(left.getElementType))
      return SINGLE_SPACING

    if (SpacingTokens.NO_SPACING_AFTER.contains(left.getElementType))
      return NO_SPACING

    return null
  }
}