package org.jetbrains.plugins.scala.injection;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression;
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory;

/**
 * Pavel Fatin
 */

public class ScalaStringLiteralManipulator extends AbstractElementManipulator<ScLiteral> {
  public ScLiteral handleContentChange(ScLiteral expr, TextRange range, String newContent) throws IncorrectOperationException {
    if (!(expr.getValue() instanceof String)) throw new IncorrectOperationException("cannot handle content change");
    String oldText = expr.getText();

    if(expr.getFirstChild().getNode().getElementType() != ScalaTokenTypes.tMULTILINE_STRING)
      newContent = StringUtil.escapeStringCharacters(newContent);

    String newText = oldText.substring(0, range.getStartOffset()) + newContent + oldText.substring(range.getEndOffset());
    final ScExpression newExpr = ScalaPsiElementFactory.createExpressionFromText(newText, expr.getManager());

    PsiElement firstChild = expr.getFirstChild();
    assert firstChild != null && firstChild.getNextSibling() == null;

    PsiElement newElement = newExpr.getFirstChild();
    assert newElement != null;
    firstChild.replace(newElement);

    return expr;
  }

  public TextRange getRangeInElement(final ScLiteral element) {
    final String text = element.getText();
    return getLiteralRange(text);
  }

  public static TextRange getLiteralRange(String text) {
    if (text.length() > 6 && text.startsWith("\"\"\"") && text.endsWith("\"\"\"")) {
      return new TextRange(3, text.length() - 3);
    }
    return new TextRange(1, Math.max(1, text.length() - 1));
  }
}