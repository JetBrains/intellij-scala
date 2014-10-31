package org.jetbrains.plugins.scala.lang.actions.editor.backspace;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.IncorrectOperationException;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.lang.actions.ActionTestBase;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

import java.io.IOException;

/**
 * User: Alexander Podkhalyuzin
 * Date: 03.07.2008
 */

@RunWith(AllTests.class)
public class BackspaceActionTest extends ActionTestBase {

  @NonNls
  private static final String DATA_PATH = "/actions/editor/backspace/data";

  protected Editor myEditor;
  protected FileEditorManager fileEditorManager;
  protected PsiFile myFile;

  public BackspaceActionTest() {
    super(System.getProperty("path") != null ?
            System.getProperty("path") :
            TestUtils.getTestDataPath() + DATA_PATH
    );
  }


  protected EditorActionHandler getMyHandler() {
    return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_BACKSPACE);
  }


  private String processFile(final PsiFile file) throws IncorrectOperationException, InvalidDataException, IOException {
    String result;
    String fileText = file.getText();
    int offset = fileText.indexOf(CARET_MARKER);
    fileText = removeMarker(fileText);
    myFile = TestUtils.createPseudoPhysicalScalaFile(getProject(), fileText);
    fileEditorManager = FileEditorManager.getInstance(LightPlatformTestCase.getProject());
    myEditor = fileEditorManager.openTextEditor(new OpenFileDescriptor(getProject(), myFile.getVirtualFile(), 0), false);
    assert myEditor != null;
    myEditor.getCaretModel().moveToOffset(offset);

    final myDataContext dataContext = getDataContext(myFile);
    final EditorActionHandler handler = getMyHandler();

    try {
      performAction(getProject(), new Runnable() {
        public void run() {
          handler.execute(myEditor, myEditor.getCaretModel().getCurrentCaret(), dataContext);
        }
      });
      offset = myEditor.getCaretModel().getOffset();
      result = myEditor.getDocument().getText();
      result = result.substring(0, offset) + CARET_MARKER + result.substring(offset);
    } finally {
      fileEditorManager.closeFile(myFile.getVirtualFile());
      myEditor = null;
    }

    return result;
  }

  public String transform(String testName, String[] data) throws Exception {
    setSettings();
    String fileText = data[0];
    final PsiFile psiFile = TestUtils.createPseudoPhysicalScalaFile(getProject(), fileText);
    return processFile(psiFile);
  }


  public static Test suite() {
    return new BackspaceActionTest();
  }
}
