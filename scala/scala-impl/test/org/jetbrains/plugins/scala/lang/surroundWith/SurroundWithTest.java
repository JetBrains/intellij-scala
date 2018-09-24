package org.jetbrains.plugins.scala.lang.surroundWith;

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import junit.framework.Test;
import org.jetbrains.plugins.scala.lang.surroundWith.descriptors.ScalaSurroundDescriptors$;
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;
import scala.Tuple4;

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.11.2008
 */
@SuppressWarnings({"ConstantConditions"})
@RunWith(AllTests.class)
public class SurroundWithTest extends BaseScalaFileSetTestCase{
  private static final String DATA_PATH = "/surroundWith/data/";


  public SurroundWithTest(String path) {
    super(path);
  }

  public static Test suite() {
    return new SurroundWithTest(TestUtils.getTestDataPath() + DATA_PATH);
  }

  private void doSurround(final Project project, final PsiFile file,
                          Surrounder surrounder, int startSelection, int endSelection) {
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    try {
      Editor editor = fileEditorManager.openTextEditor(new OpenFileDescriptor(getProject(), file.getVirtualFile(), 0), false);
      editor.getSelectionModel().setSelection(startSelection, endSelection);
      SurroundWithHandler.invoke(project, editor, file, surrounder);
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      fileEditorManager.closeFile(file.getVirtualFile());
    }
  }

  public String transform(String testName, String[] data) throws Exception {
    Tuple4<String, Integer, Integer, Integer> res = SurroundWithTestUtil.prepareFile(data[0]);
    String fileText = res._1();
    final int startSelection = res._2();
    final int endSelection = res._3();
    final int surroundType = res._4();
    final PsiFile psiFile = TestUtils.createPseudoPhysicalScalaFile(getProject(), fileText);

    final Surrounder[] surrounder = ScalaSurroundDescriptors$.MODULE$.getSurroundDescriptors()[0].getSurrounders();
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            doSurround(getProject(), psiFile, surrounder[surroundType], startSelection, endSelection);
          }
        });
      }
    }, null, null);

    return psiFile.getText();
  }
}
