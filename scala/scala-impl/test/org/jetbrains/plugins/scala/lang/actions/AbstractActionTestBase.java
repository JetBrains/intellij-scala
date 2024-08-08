package org.jetbrains.plugins.scala.lang.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.WriteCommandAction;
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

abstract public class AbstractActionTestBase extends ActionTestBase {
  private Editor myEditor;

  protected AbstractActionTestBase(@NotNull @NonNls String dataPath) {
    super(dataPath);
  }

  protected abstract EditorActionHandler getMyHandler();

  @NotNull
  private String processFile(@NotNull PsiFile file,
                             @NotNull Project project) throws IncorrectOperationException, InvalidDataException {
    final String result;

    String fileText = file.getText();
    int offset = fileText.indexOf(CARET_MARKER);
    fileText = removeMarker(fileText);

    PsiFile psiFile = createLightFile(fileText, project);
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);

    myEditor = fileEditorManager.openTextEditor(new OpenFileDescriptor(project, psiFile.getVirtualFile(), 0), false);
    assert myEditor != null;
    myEditor.getCaretModel().moveToOffset(offset);

    final DataContext dataContext = getDataContext(psiFile);
    final EditorActionHandler handler = getMyHandler();

    try {
      WriteCommandAction.runWriteCommandAction(project, () -> {
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

  @Override
  @NotNull
  protected String transform(@NotNull String testName,
                             @NotNull String fileText,
                             @NotNull Project project) {
    final PsiFile psiFile = createLightFile(fileText, project);
    return processFile(psiFile, project);
  }
}
