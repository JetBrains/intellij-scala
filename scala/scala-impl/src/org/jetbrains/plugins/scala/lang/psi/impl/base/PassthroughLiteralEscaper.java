package org.jetbrains.plugins.scala.lang.psi.impl.base;

import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.LiteralTextEscaper;
import org.jetbrains.annotations.NotNull;

/**
 * Pavel Fatin
 */

public class PassthroughLiteralEscaper extends LiteralTextEscaper<ScLiteralImpl> {
  public PassthroughLiteralEscaper(final ScLiteralImpl literal) {
    super(literal);
  }

  public boolean decode(@NotNull final TextRange rangeInsideHost, @NotNull StringBuilder outChars) {
    ProperTextRange.assertProperRange(rangeInsideHost);
    outChars.append(myHost.getText(), rangeInsideHost.getStartOffset(), rangeInsideHost.getEndOffset());
    return true;
  }

  public int getOffsetInHost(int offsetInDecoded, @NotNull final TextRange rangeInsideHost) {
    int offset = offsetInDecoded + rangeInsideHost.getStartOffset();
    if (offset < rangeInsideHost.getStartOffset()) offset = rangeInsideHost.getStartOffset();
    if (offset > rangeInsideHost.getEndOffset()) offset = rangeInsideHost.getEndOffset();
    return offset;
  }

  public boolean isOneLine() {
    final Object value = myHost.getValue();
    return value instanceof String && ((String)value).indexOf('\n') < 0;
  }
}
