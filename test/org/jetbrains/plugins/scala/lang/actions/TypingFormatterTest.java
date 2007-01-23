package org.jetbrains.plugins.scala.lang.actions;

import com.intellij.codeInsight.editorActions.EnterHandler;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.idea.IdeaTestApplication;
import junit.framework.Assert;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;
import org.jetbrains.plugins.scala.util.TestUtils;

import java.io.IOException;


public class TypingFormatterTest extends BaseScalaFileSetTestCase {

  @NonNls
  protected static final String CARET_MARKER = "<caret>";
  private static final String DATA_PATH = "test/org/jetbrains/plugins/scala/lang/actions/data/enter";

  protected Editor myEditor;
  protected FileEditorManager fileEditorManager;
  protected String newDocumentText;
  protected PsiFile myFile;

  public TypingFormatterTest() {
    super(System.getProperty("path") != null ?
      System.getProperty("path") :
      DATA_PATH
    );
  }

  public static void runAsWriteAction(final Runnable runnable) {
    ApplicationManager.getApplication().runWriteAction(runnable);
  }

  public static void performAction(final Project project, final Runnable action) {
    runAsWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(project, action, "Execution", null);
      }
    });
  }

  protected String removeMarker(String text) {
    int index = text.indexOf(CARET_MARKER);
    return text.substring(0, index) + text.substring(index + CARET_MARKER.length());
  }

  private String typeEnter(final PsiFile file) throws IncorrectOperationException, InvalidDataException, IOException {

    String result;
    String fileText = file.getText();
    int offset = fileText.indexOf(CARET_MARKER);
    fileText = removeMarker(fileText);

    myFile = TestUtils.createPseudoPhysicalFile(project, fileText);
    fileEditorManager = FileEditorManager.getInstance(project);
    myEditor = fileEditorManager.openTextEditor(new OpenFileDescriptor(project, myFile.getVirtualFile(), 0), false);
    myEditor.getCaretModel().moveToOffset(offset);

    EditorActionManager manager = EditorActionManager.getInstance();
    final EditorActionHandler handler = new EnterHandler(manager.getActionHandler(IdeActions.ACTION_EDITOR_ENTER));
    manager.setActionHandler(IdeActions.ACTION_EDITOR_ENTER, handler);

    final myDataContext dataContext = new myDataContext();
    IdeaTestApplication.getInstance().setDataProvider(dataContext);
    Assert.assertTrue(handler.isEnabled(myEditor, dataContext));

    try {

      performAction(project, new Runnable() {
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

  protected String performTyping(final Project project, final PsiFile file) throws IncorrectOperationException, InvalidDataException, IOException {
    setSettings();
    return typeEnter(file);
  }

  public String transform(String testName, String[] data) throws Exception {
    String fileText = data[0];
    final PsiFile psiFile = TestUtils.createPseudoPhysicalFile(project, fileText);
    return performTyping(project, psiFile);
  }


  public static Test suite() {
    return new TypingFormatterTest();
  }

  public class myDataContext implements DataContext, DataProvider {
    @Nullable
    public Object getData(@NonNls String dataId) {
      if (DataConstants.LANGUAGE.equals(dataId)) return myFile.getLanguage();
      if (DataConstants.PROJECT.equals(dataId)) return myFile.getProject();
      return null;
    }
  }

}
