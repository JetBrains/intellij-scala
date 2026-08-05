package org.jetbrains.plugins.scala.lang.lexer;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings;

/** Resolves backend project settings before constructing the shared-safe Scala lexer. */
public final class ScalaLexerFactory {
  private ScalaLexerFactory() {}

  public static ScalaLexer create(boolean isScala3, @Nullable Project project) {
    boolean treatDocCommentAsBlockComment = project != null &&
        ScalaProjectSettings.getInstance(project).isTreatDocCommentAsBlockComment();
    return new ScalaLexer(isScala3, new ScalaLexerConfig(treatDocCommentAsBlockComment));
  }
}
