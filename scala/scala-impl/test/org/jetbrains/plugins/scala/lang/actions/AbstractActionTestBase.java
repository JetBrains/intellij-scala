package org.jetbrains.plugins.scala.lang.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.scala.util.TestUtils;

import java.io.IOException;

abstract public class AbstractActionTestBase extends ActionTestBase {
  private Editor myEditor;

  public AbstractActionTestBase(String dataPath) {
    super(baseTestPath() + dataPath);
  }

  private static String baseTestPath() {
    String systemPath = System.getProperty("path");
    return systemPath != null ? systemPath : TestUtils.getTestDataPath();
  }

  protected abstract EditorActionHandler getMyHandler();

  private String processFile(final PsiFile file) throws IncorrectOperationException, InvalidDataException, IOException {
    final String result;

    String fileText = file.getText();
    int offset = fileText.indexOf(CARET_MARKER);
    fileText = removeMarker(fileText);

    PsiFile psiFile = TestUtils.createPseudoPhysicalScalaFile(getProject(), fileText);
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(getProject());

    myEditor = fileEditorManager.openTextEditor(new OpenFileDescriptor(getProject(), psiFile.getVirtualFile(), 0), false);
    assert myEditor != null;
    myEditor.getCaretModel().moveToOffset(offset);

    final MyDataContext dataContext = getDataContext(psiFile);
    final EditorActionHandler handler = getMyHandler();

    try {
      performAction(getProject(), () -> {
        handler.execute(myEditor, myEditor.getCaretModel().getCurrentCaret(), dataContext);
      });

      offset = myEditor.getCaretModel().getOffset();
      String editorText = myEditor.getDocument().getText();
      result = editorText.substring(0, offset) + CARET_MARKER + editorText.substring(offset);
    } finally {
      fileEditorManager.closeFile(psiFile.getVirtualFile());
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

}
