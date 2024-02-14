package org.jetbrains.plugins.scala.lang.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.base.ScalaFileSetTestCase;

public abstract class ActionTestBase extends ScalaFileSetTestCase {

  protected static final String CARET_MARKER = "<caret>";

  protected ActionTestBase(@NotNull @NonNls String path) {
    super(path);
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

  public static class MyDataContext implements DataContext, DataProvider {

    PsiFile myFile;

    public MyDataContext(PsiFile file) {
      myFile = file;
    }

    @Override
    @Nullable
    public Object getData(@NotNull @NonNls String dataId) {
      if (LangDataKeys.LANGUAGE.is(dataId)) return myFile.getLanguage();
      if (PlatformCoreDataKeys.PROJECT.is(dataId)) return myFile.getProject();
      return null;
    }
  }
}
