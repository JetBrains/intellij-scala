package org.jetbrains.plugins.scala.lang.psi.impl.base;

import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import org.jetbrains.annotations.NotNull;

/**
 * Pavel Fatin
 */

public class ScLiteralEscaper extends LiteralTextEscaper<ScLiteralImpl> {
  private int[] outSourceOffsets;

  public ScLiteralEscaper(final ScLiteralImpl literal) {
    super(literal);
  }

  public boolean decode(@NotNull final TextRange rangeInsideHost, @NotNull StringBuilder outChars) {
    ProperTextRange.assertProperRange(rangeInsideHost);
    String subText = rangeInsideHost.substring(myHost.getText());
    outSourceOffsets = new int[subText.length() + 1];
    return PsiLiteralExpressionImpl.parseStringCharacters(subText, outChars, outSourceOffsets);
  }

  public int getOffsetInHost(int offsetInDecoded, @NotNull final TextRange rangeInsideHost) {
    int result = offsetInDecoded < outSourceOffsets.length ? outSourceOffsets[offsetInDecoded] : -1;
    if (result == -1) return -1;
    return (result <= rangeInsideHost.getLength() ? result : rangeInsideHost.getLength()) + rangeInsideHost.getStartOffset();
  }

  public boolean isOneLine() {
    final Object value = myHost.getValue();
    return value instanceof String && ((String)value).indexOf('\n') < 0;
  }
}
