package org.jetbrains.plugins.scala.lang.lexer;

import com.intellij.lexer.LayeredLexer;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.scala.lang.lexer.core.ScalaCoreLexer;
import org.jetbrains.plugins.scala.lang.lexer.core.ScalaSplittingLexer;

/**
 * User: Dmitry Naydanov
 * Date: 6/19/12
 */
public class ScalaLayeredPlainLexer extends LayeredLexer {
  public ScalaLayeredPlainLexer(boolean treatDocCommentAsBlockComment) {
    super(new ScalaSplittingLexer(treatDocCommentAsBlockComment));
//    registerLayer(new ScalaCoreLexer(), ScalaTokenTypesEx.SCALA_PLAIN_CONTENT);
    Boolean flagValue = null;
    if (ourDisableLayersFlag != null) {
      flagValue = ourDisableLayersFlag.get();
      ourDisableLayersFlag.set(false);
    }
    registerSelfStoppingLayer(new ScalaCoreLexer(), new IElementType[]{ScalaTokenTypesEx.SCALA_PLAIN_CONTENT}, IElementType.EMPTY_ARRAY);
    if (ourDisableLayersFlag != null) {
      ourDisableLayersFlag.set(flagValue);
    }
  }

  public ScalaLayeredPlainLexer() {
    this(false);
  }
}
