package org.jetbrains.plugins.scala.lang.actions;

import com.intellij.openapi.actionSystem.CustomizedDataContext;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.base.ScalaFileSetTestCase;

public abstract class ActionTestBase extends ScalaFileSetTestCase {

  protected static final String CARET_MARKER = "<caret>";
  protected int myOffset;

  protected ActionTestBase(@NotNull @NonNls String path) {
    super(path);
  }

  public static void runAsWriteAction(final Runnable runnable) {
    ApplicationManager.getApplication().runWriteAction(runnable);
  }

  /**
   * Returns context for action performing
   */
  @ApiStatus.Internal
  public static @NotNull DataContext getDataContext(PsiFile file) throws InvalidDataException {
    return CustomizedDataContext.withProvider(CustomizedDataContext.EMPTY_CONTEXT, dataId -> {
      if (LangDataKeys.LANGUAGE.is(dataId)) return file.getLanguage();
      if (PlatformCoreDataKeys.PROJECT.is(dataId)) return file.getProject();
      return null;
    });
  }

  /**
   * Removes CARET_MARKER from file text
   */
  protected String removeMarker(String text) {
    int index = text.indexOf(CARET_MARKER);
    return text.substring(0, index) + text.substring(index + CARET_MARKER.length());
  }
}
