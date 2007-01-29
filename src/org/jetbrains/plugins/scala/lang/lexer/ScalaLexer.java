package org.jetbrains.plugins.scala.lang.lexer;

import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.psi.tree.TokenSet;

/**
 * Created by IntelliJ IDEA.
 * User: Ilya.Sergey
 * Date: 29.01.2007
 * Time: 18:29:28
 * To change this template use File | Settings | File Templates.
 */
public class ScalaLexer extends MergingLexerAdapter {

  public ScalaLexer() {
    super(new ScalaFlexLexer(),
      TokenSet.create(
        ScalaTokenTypes.tWHITE_SPACE_IN_LINE,
        ScalaTokenTypes.tNON_SIGNIFICANT_NEWLINE,
        ScalaTokenTypes.tCOMMENT_CONTENT
      ));
  }
}
