package org.jetbrains.plugins.scala.lang.formatter;

import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.annotations.NonNls;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.FileTypeIndentOptionsProvider;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.IncorrectOperationException;
import junit.framework.Assert;
import junit.framework.Test;

/**
 * Created by IntelliJ IDEA.
 * User: Ilya.Sergey
 */

public class FormatterTest extends BaseScalaFileSetTestCase {
  @NonNls
  private static final String DATA_PATH = "test/org/jetbrains/plugins/scala/lang/formatter/data/";

  protected CodeStyleSettings mySettings;

  public FormatterTest() {
    super(System.getProperty("path") != null ?
            System.getProperty("path") :
            DATA_PATH
    );
  }

  private CodeStyleSettings getSettings() {
    return CodeStyleSettingsManager.getSettings(project);
  }

  protected void performFormatting(final Project project, final PsiFile file) throws IncorrectOperationException {
    mySettings = getSettings();
    mySettings.getIndentOptions(ScalaFileType.SCALA_FILE_TYPE).INDENT_SIZE = 2;
    mySettings.getIndentOptions(ScalaFileType.SCALA_FILE_TYPE).CONTINUATION_INDENT_SIZE = 2;
    mySettings.getIndentOptions(ScalaFileType.SCALA_FILE_TYPE).TAB_SIZE = 2;
    
    TextRange myTextRange = file.getTextRange();
    CodeStyleManager.getInstance(project).reformatText(file, myTextRange.getStartOffset(), myTextRange.getEndOffset());
  }


  public String transform(String testName, String[] data) throws Exception {
    String fileText = data[0];
    final PsiFile psiFile = TestUtils.createPseudoPhysicalFile(project, fileText);
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              performFormatting(project, psiFile);
            } catch (IncorrectOperationException e) {
              e.printStackTrace();
            }
          }
        });
      }
    }, null, null);
    return psiFile.getText();
  }

  public static Test suite() {
    return new FormatterTest();
  }

}



