package org.jetbrains.plugins.scala.lang.parser.util;

import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * User: Dmitry Naydanov
 * Date: 6/27/12
 */
public abstract class ParserPatcher {
  public static ExtensionPointName<ParserPatcher> EP_NAME = ExtensionPointName.create("org.intellij.scala.scalaParserPatcher");
  public static final ParserPatcher defaultPatcher = new ParserPatcher(){};
  
  public boolean canPatch(PsiBuilder builder) {
    return false;
  }
  public boolean parse(PsiBuilder builder) {
    return false;
  }

  private static ParserPatcher[] allPatchers = null;

  @NotNull 
  public static ParserPatcher[] getAllPatchers() {
    if (allPatchers == null) {
      allPatchers = EP_NAME.getExtensions();
    }
    return allPatchers;
  }

  @NotNull
  public static ParserPatcher getPatcher() {
    final ParserPatcher[] patchers = getAllPatchers();

    if (patchers.length > 0) return patchers[0]; else return defaultPatcher;
  }

  @NotNull
  public static ParserPatcher getSuitablePatcher(PsiBuilder targetBuilder) {
    for (ParserPatcher patcher : getAllPatchers()) {
      if (patcher.canPatch(targetBuilder)) return patcher;
    }

    return defaultPatcher;
  }
}
