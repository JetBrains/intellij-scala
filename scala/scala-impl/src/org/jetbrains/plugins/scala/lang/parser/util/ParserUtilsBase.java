package org.jetbrains.plugins.scala.lang.parser.util;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;

/**
 * @author ilyas
 */
public abstract class ParserUtilsBase {

  /**
   * Checks, that following element sequence is like given
   *
   * @param builder Given PsiBuilder
   * @param elems   Array of need elements in order
   * @return true if following sequence is like a given
   */
  public boolean lookAhead(PsiBuilder builder, IElementType... elems) {
    if (!elems[0].equals(builder.getTokenType())) return false;

    if (elems.length == 1) return true;

    PsiBuilder.Marker rb = builder.mark();
    builder.advanceLexer();
    int i = 1;
    while (!builder.eof() && i < elems.length && elems[i].equals(builder.getTokenType())) {
      builder.advanceLexer();
      i++;
    }
    rb.rollbackTo();
    return i == elems.length;
  }
}
