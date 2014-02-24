package org.jetbrains.plugins.scala.lang.formatter.automatic;

import com.intellij.formatting.Indent;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.util.IncorrectOperationException;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock;
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.ScalaFormattingRuleMatcher;
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;
import org.jetbrains.plugins.scala.util.TestUtils;

import java.io.File;
import java.io.IOException;

public class ExtractAndFormatTest extends BaseScalaFileSetTestCase {

  @NonNls
  private static final String DATA_PATH = "/formatter/automatic/data/format/extractAndFormat";

  public ExtractAndFormatTest() throws IOException {
    super(
        System.getProperty("path") != null ?
            System.getProperty("path") :
            (new File(TestUtils.getTestDataPath() + DATA_PATH)).getCanonicalPath()
    );
  }

  public ScalaBlock getRoot(String code) {
    Project project = getProject();

    PsiFile containingFile = TestUtils.createPseudoPhysicalScalaFile(project, code);

    assert(containingFile.getFileType() == ScalaFileType.SCALA_FILE_TYPE);

    ASTNode astNode = containingFile.getNode();
    assert(astNode != null);
    CodeStyleSettings codeStyleSettings = new CodeStyleSettings(); //TODO: probably replace it. Although it actually matters not for parameter extraction for now
    return new ScalaBlock(null, astNode, null, null, Indent.getAbsoluteNoneIndent(), null, codeStyleSettings);
  }

  protected void performFormatting(final Project project, final PsiFile file) throws IncorrectOperationException {
    TextRange myTextRange = file.getTextRange();
    CodeStyleManager.getInstance(project).reformatText(file, myTextRange.getStartOffset(), myTextRange.getEndOffset());
  }

  public String transform(String testName, String[] data) {

    String learnCode = data[0];

    String formatCode = data[1];

    ScalaBlock.initFormatter(getRoot(learnCode));

    ScalaBlock.feedFormatter(getRoot(formatCode));

    final PsiFile psiFile = TestUtils.createPseudoPhysicalScalaFile(getProject(), formatCode);
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              performFormatting(getProject(), psiFile);
            } catch (IncorrectOperationException e) {
              e.printStackTrace();
            }
          }
        });
      }
    }, null, null);
    return psiFile.getText();
  }

  public static Test suite() throws IOException {
    return new ExtractAndFormatTest();
  }

}
