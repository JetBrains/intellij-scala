package org.jetbrains.plugins.scala.lang.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.util.TestUtils;

import java.io.IOException;

abstract public class AbstractActionTestBase extends ActionTestBase {
  private Editor myEditor;

  protected AbstractActionTestBase(@NotNull @NonNls String dataPath) {
    super(dataPath);
  }

  protected abstract EditorActionHandler getMyHandler();

  @NotNull
  private String processFile(@NotNull PsiFile file,
                             @NotNull Project project) throws IncorrectOperationException, InvalidDataException, IOException {
    final String result;

    String fileText = file.getText();
    int offset = fileText.indexOf(CARET_MARKER);
    fileText = removeMarker(fileText);

    PsiFile psiFile = TestUtils.createPseudoPhysicalScalaFile(project, fileText);
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);

    myEditor = fileEditorManager.openTextEditor(new OpenFileDescriptor(project, psiFile.getVirtualFile(), 0), false);
    assert myEditor != null;
    myEditor.getCaretModel().moveToOffset(offset);

    final MyDataContext dataContext = getDataContext(psiFile);
    final EditorActionHandler handler = getMyHandler();

    try {
      performAction(project, () -> {
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

  @NotNull
  protected String transform(@NotNull String testName,
                             @NotNull String fileText,
                             @NotNull Project project) throws IOException {
    final PsiFile psiFile = TestUtils.createPseudoPhysicalScalaFile(project, fileText);
    return processFile(psiFile, project);
  }

}
