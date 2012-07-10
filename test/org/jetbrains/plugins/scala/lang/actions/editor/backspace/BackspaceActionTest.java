package org.jetbrains.plugins.scala.lang.actions.editor.backspace;

import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.plugins.scala.Console;
import org.jetbrains.plugins.scala.lang.actions.ActionTestBase;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.jetbrains.annotations.NonNls;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;

import java.io.IOException;

import junit.framework.Test;

/**
 * User: Alexander Podkhalyuzin
 * Date: 03.07.2008
 */

public class BackspaceActionTest extends ActionTestBase {

  @NonNls
  private static final String DATA_PATH = "./test/org/jetbrains/plugins/scala/lang/actions/editor/backspace/data/";

  protected Editor myEditor;
  protected FileEditorManager fileEditorManager;
  protected String newDocumentText;
  protected PsiFile myFile;

  public BackspaceActionTest() {
    super(System.getProperty("path") != null ?
            System.getProperty("path") :
            DATA_PATH
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
    myEditor.getCaretModel().moveToOffset(offset);

    final myDataContext dataContext = getDataContext(myFile);
    final EditorActionHandler handler = getMyHandler();

    try {
      performAction(getProject(), new Runnable() {
        public void run() {
          handler.execute(myEditor, dataContext);
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
    String result = processFile(psiFile);
    Console.println("------------------------ " + testName + " ------------------------");
    Console.println(result);
    Console.println("");
    return result;
  }


  public static Test suite() {
    return new BackspaceActionTest();
  }
}
