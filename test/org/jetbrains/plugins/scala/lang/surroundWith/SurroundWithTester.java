package org.jetbrains.plugins.scala.lang.surroundWith;

import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;
import org.jetbrains.plugins.scala.util.TestUtils;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.lang.surroundWith.Surrounder;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 22.01.2007
 */
abstract public class SurroundWithTester extends BaseScalaFileSetTestCase {
  private static final String DATA_PATH = "test/org/jetbrains/plugins/scala/lang/surroundWith/data/";

  protected String getDataPath() {
    return "test/org/jetbrains/plugins/scala/lang/surroundWith/data/";
  }

//  static String getDataPath() {
//    return "test/org/jetbrains/plugins/scala/lang/surroundWith/data/";
//  }
//  private Surrounder surrounder;

  public SurroundWithTester(String datePath) {
    super(datePath);
  }

  protected void selectContentInTemplateBody(PsiFile file, Editor editor) {
    PsiElement classDef = file.getFirstChild();
    PsiElement topDefTmpl = classDef.getLastChild();
    PsiElement templateBody = topDefTmpl.getFirstChild();

    PsiElement expression = templateBody.getChildren()[0];

    editor.getSelectionModel().setSelection(expression.getTextRange().getStartOffset(), expression.getTextRange().getEndOffset());
  }

  protected void doSurround(final Project project, final PsiFile file, Surrounder surrounder) throws IncorrectOperationException {
//    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();

    PsiFile myFile;
    FileEditorManager fileEditorManager;
    Editor editor;
    myFile = TestUtils.createPseudoPhysicalFile(project, file.getText());
    fileEditorManager = FileEditorManager.getInstance(project);

    try {
      editor = fileEditorManager.openTextEditor(new OpenFileDescriptor(project, myFile.getVirtualFile(), 0), false);

      selectContentInTemplateBody(file, editor);
      SurroundWithHandler.invoke(project, editor, file, surrounder);
    } catch (Exception e){
      e.printStackTrace();
    } finally{

    fileEditorManager.closeFile(myFile.getVirtualFile());
    editor = null;
  }
  }

  public String transform(String testName, String[] data) throws Exception {
    String fileText = data[0];
    final PsiFile psiFile = TestUtils.createPseudoPhysicalFile(project, fileText);

    final Surrounder surrounder = surrounder();
//    for (final Surrounder surrounder : surrounders) {
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              doSurround(project, psiFile, surrounder);
            } catch (IncorrectOperationException e) {
              e.printStackTrace();
            }
          }
        });
      }
    }, null, null);

//    }
    return psiFile.getText();
  }

  abstract public Surrounder surrounder();
//    return ScalaToolsFactory.getInstance().createSurroundDescriptors().getSurroundDescriptors()[0].getSurrounders();

}