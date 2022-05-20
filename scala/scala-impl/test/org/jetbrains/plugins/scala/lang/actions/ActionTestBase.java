package org.jetbrains.plugins.scala.lang.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
  protected MyDataContext getDataContext(PsiFile file) throws InvalidDataException {
    return new MyDataContext(file);
  }

  /**
   * Removes CARET_MARKER from file text
   */
  protected String removeMarker(String text) {
    int index = text.indexOf(CARET_MARKER);
    return text.substring(0, index) + text.substring(index + CARET_MARKER.length());
  }

  public static void performAction(final Project project, final Runnable action) {
    runAsWriteAction(() -> CommandProcessor.getInstance().executeCommand(project, action, "Execution", null));
  }

  public static class MyDataContext implements DataContext, DataProvider {

    PsiFile myFile;

    public MyDataContext(PsiFile file) {
      myFile = file;
    }

    @Nullable
    public Object getData(@NotNull @NonNls String dataId) {
      if (LangDataKeys.LANGUAGE.is(dataId)) return myFile.getLanguage();
      if (PlatformCoreDataKeys.PROJECT.is(dataId)) return myFile.getProject();
      return null;
    }
  }
}
