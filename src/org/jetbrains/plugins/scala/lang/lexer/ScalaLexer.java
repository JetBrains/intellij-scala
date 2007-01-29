package org.jetbrains.plugins.scala.lang.lexer;

import com.intellij.lexer.MergingLexerAdapter;

/**
 * Created by IntelliJ IDEA.
 * User: Ilya.Sergey
 * Date: 29.01.2007
 * Time: 18:29:28
 * To change this template use File | Settings | File Templates.
 */
public class ScalaLexer extends MergingLexerAdapter {

  public ScalaLexer() {
    super(new ScalaFlexLexer(), ScalaTokenTypes.WHITES_SPACES_TOKEN_SET);
  }
}
