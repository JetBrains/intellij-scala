package org.jetbrains.plugins.scala.refactoring.introduceVariable;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import junit.framework.Assert;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.lang.actions.ActionTestBase;
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression;
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses;
import org.jetbrains.plugins.scala.lang.psi.types.ScType;
import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.ScalaIntroduceVariableHandler;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil;
import scala.Some;

import java.io.IOException;

/**
 * User: Alexander Podkhalyuzin
 * Date: 02.07.2008
 */


public class IntroduceVariableTest extends ActionTestBase {

  @NonNls
  private static final String DATA_PATH = "test/org/jetbrains/plugins/scala/refactoring/introduceVariable/data/";

  protected static final String ALL_MARKER = "<all>";

  protected Editor myEditor;
  protected FileEditorManager fileEditorManager;
  protected String newDocumentText;
  protected PsiFile myFile;


  protected boolean replaceAllOccurences = false;

  public IntroduceVariableTest() {
    super(System.getProperty("path") != null ?
            System.getProperty("path") :
            DATA_PATH
    );
    replaceAllOccurences = System.getProperty("replaceAll") != null &&
            Boolean.parseBoolean(System.getProperty("path"));
  }


  private String processFile(final PsiFile file) throws IncorrectOperationException, InvalidDataException, IOException {
    myProject.getComponent(SyntheticClasses.class).registerClasses();
    String result = "";
    String fileText = file.getText();
    int startOffset = fileText.indexOf(TestUtils.BEGIN_MARKER);
    if (startOffset < 0) {
      startOffset = fileText.indexOf(ALL_MARKER);
      replaceAllOccurences = true;
      fileText = removeAllMarker(fileText);
    } else {
      replaceAllOccurences = false;
      fileText = TestUtils.removeBeginMarker(fileText);
    }
    int endOffset = fileText.indexOf(TestUtils.END_MARKER);
    fileText = TestUtils.removeEndMarker(fileText);
    myFile = TestUtils.createPseudoPhysicalScalaFile(myProject, fileText);
    fileEditorManager = FileEditorManager.getInstance(myProject);
    myEditor = fileEditorManager.openTextEditor(new OpenFileDescriptor(myProject, myFile.getVirtualFile(), 0), false);

    try {
      myEditor.getSelectionModel().setSelection(startOffset, endOffset);

      // gathering data for introduce variable
      ScalaIntroduceVariableHandler introduceVariableHandler = new ScalaIntroduceVariableHandler();

      Assert.assertTrue(myFile instanceof ScalaFile);
      ScExpression selectedExpr = null;
      if (ScalaRefactoringUtil.getExpression(myProject, myEditor, myFile, startOffset, endOffset) instanceof Some) {
        Some temp = (Some) ScalaRefactoringUtil.getExpression(myProject, myEditor, myFile, startOffset, endOffset);
        selectedExpr = (ScExpression) temp.get();
      }
      //findElementInRange(((ScalaFileBase) myFile), startOffset, endOffset, GrExpression.class);

      Assert.assertNotNull("Selected expression reference points to null", selectedExpr);

      final PsiElement tempContainer = ScalaRefactoringUtil.getEnclosingContainer(selectedExpr);
      Assert.assertTrue(tempContainer instanceof ScalaPsiElement);

      ScExpression[] occurences = ScalaRefactoringUtil.getOccurrences(ScalaRefactoringUtil.unparExpr(selectedExpr), tempContainer);
      String varName = "value";
      final ScType varType = selectedExpr.getType();

      introduceVariableHandler.runRefactoring(selectedExpr, myEditor, tempContainer, occurences, varName, varType, replaceAllOccurences, false);


      result = myEditor.getDocument().getText();
      int caretOffset = myEditor.getCaretModel().getOffset();
      result = result.substring(0, caretOffset) + TestUtils.CARET_MARKER + result.substring(caretOffset);
    } finally {
      fileEditorManager.closeFile(myFile.getVirtualFile());
      myEditor = null;
    }

    return result;
  }


  public String transform(String testName, String[] data) throws Exception {
    setSettings();
    String fileText = data[0];
    final PsiFile psiFile = TestUtils.createPseudoPhysicalScalaFile(myProject, fileText);
    String result = processFile(psiFile);
    System.out.println("------------------------ " + testName + " ------------------------");
    System.out.println(result);
    System.out.println("");
    return result;
  }


  public static Test suite() {
    return new IntroduceVariableTest();
  }

  protected String removeAllMarker(String text) {
    int index = text.indexOf(ALL_MARKER);
    myOffset = index - 1;
    return text.substring(0, index) + text.substring(index + ALL_MARKER.length());
  }

}