package org.jetbrains.plugins.scala.failed.rearranger;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.arrangement.engine.ArrangementEngine;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtilRt;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

import java.io.File;
import java.io.IOException;

/**
 * @author Roman.Shein
 * Date: 26.07.13
 */
@RunWith(AllTests.class)
public class RearrangerTest extends BaseScalaFileSetTestCase {
  @NonNls
  private static final String DATA_PATH = "/rearranger/failedData";

  public RearrangerTest() throws IOException {
    super(System.getProperty("path") != null ? System.getProperty("path") : (new File(TestUtils.getTestDataPath() + DATA_PATH)).getCanonicalPath());
  }

  @Override
  public String transform(String testName, String[] data) {
    String fileText = data[0];
    Project project = getProject();
    final PsiFile file = TestUtils.createPseudoPhysicalScalaFile(project, fileText);
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              rearrange(file);
            } catch (IncorrectOperationException e) {
              e.printStackTrace();
            }
          }
        });
      }
    }, null, null);
    return file.getText();
  }

  private void rearrange(PsiFile file) {
    Project project = getProject();
    final ArrangementEngine engine = ServiceManager.getService(project, ArrangementEngine.class);
    engine.arrange(file, ContainerUtilRt.newArrayList(file.getTextRange()));
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(file.getProject());
    Document document = documentManager.getDocument(file);
    if (document != null) {
      documentManager.commitDocument(document);
    } else {
      throw new IllegalArgumentException("Wrong PsiFile type provided: the file has no document.");
    }
  }

  public static Test suite() throws IOException {
    return new RearrangerTest();
  }
}