package org.jetbrains.plugins.scalaDoc.lang.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LightPsiParser;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing;

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.07.2008
 */
public class ScalaDocParser implements PsiParser, LightPsiParser {

  @Override
  public void parseLight(@NotNull IElementType root, @NotNull PsiBuilder builder) {
    new MyScaladocParsing(builder).parse(root);
  }

  @NotNull
  public ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
    parseLight(root, builder);
    return builder.getTreeBuilt();
  }
}
