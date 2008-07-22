package org.jetbrains.plugins.scala.lang.scaladoc.parser;

import com.intellij.lang.PsiParser;
import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.ScalaDocParsing;

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.07.2008
 */
public class ScalaDocParser implements PsiParser {
  @NotNull
  public ASTNode parse(IElementType root, PsiBuilder builder) {
    PsiBuilder.Marker rootMarker = builder.mark();
    new ScalaDocParsing().parse(builder);
    rootMarker.done(root);
    return builder.getTreeBuilt();
  }
}
