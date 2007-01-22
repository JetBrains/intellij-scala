package org.jetbrains.plugins.scala.util;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;

/**
 * Created by IntelliJ IDEA.
 * User: Ilya.Sergey
 */
public class TestUtils {

  protected final static String TEMP_FILE = "temp.scala";

  public static PsiFile createPseudoPhysicalFile(final Project project, final String text) throws IncorrectOperationException {
      return PsiManager.getInstance(project).getElementFactory().createFileFromText(TEMP_FILE, FileTypeManager.getInstance().getFileTypeByFileName(TEMP_FILE),
              text, LocalTimeCounter.currentTime(), true);
  }
  

}
