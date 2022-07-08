package org.jetbrains.plugins.scalaDoc.lang.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LightPsiParser;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing;

public class ScalaDocParser implements PsiParser, LightPsiParser {
  private final int tabSize;

  public ScalaDocParser(int tabSize) {
    this.tabSize = tabSize;
  }

  @Override
  public void parseLight(@NotNull IElementType root, @NotNull PsiBuilder builder) {
    new MyScaladocParsing(builder, tabSize).parse(root);
  }

  @Override
  @NotNull
  public ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
    parseLight(root, builder);
    return builder.getTreeBuilt();
  }
}
