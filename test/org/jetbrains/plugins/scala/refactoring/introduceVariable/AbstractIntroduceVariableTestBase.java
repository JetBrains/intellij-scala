package org.jetbrains.plugins.scala.refactoring.introduceVariable;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import junit.framework.Assert;
import org.jetbrains.plugins.scala.lang.actions.ActionTestBase;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression;
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses;
import org.jetbrains.plugins.scala.lang.psi.types.ScType;
import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.ScalaIntroduceVariableHandler;
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil;
import org.jetbrains.plugins.scala.util.TestUtils;
import scala.Some;
import scala.Tuple2;

import java.io.IOException;

/**
 * User: Alexander Podkhalyuzin
 * Date: 02.07.2008
 */


abstract public class AbstractIntroduceVariableTestBase extends ActionTestBase {



  protected static final String ALL_MARKER = "<all>";

  protected Editor myEditor;
  protected FileEditorManager fileEditorManager;
  protected String newDocumentText;
  protected PsiFile myFile;


  protected boolean replaceAllOccurences = false;

  public AbstractIntroduceVariableTestBase(String DATA_PATH) {
    super(System.getProperty("path") != null ?
            System.getProperty("path") :
            DATA_PATH
    );
    replaceAllOccurences = System.getProperty("replaceAll") != null &&
            Boolean.parseBoolean(System.getProperty("path"));
  }


  private String processFile(final PsiFile file) throws IncorrectOperationException, InvalidDataException, IOException {
    final SyntheticClasses syntheticClasses = getProject().getComponent(SyntheticClasses.class);
    if (!syntheticClasses.isClassesRegistered()) {
      syntheticClasses.registerClasses();
    }
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
    myFile = TestUtils.createPseudoPhysicalScalaFile(getProject(), fileText);
    fileEditorManager = FileEditorManager.getInstance(getProject());
    VirtualFile virtualFile = myFile.getVirtualFile();
    assert virtualFile != null;
    myEditor = fileEditorManager.openTextEditor(new OpenFileDescriptor(getProject(), virtualFile, 0), false);
    assert myEditor != null;

    try {
      myEditor.getSelectionModel().setSelection(startOffset, endOffset);

      // gathering data for introduce variable
      ScalaIntroduceVariableHandler introduceVariableHandler = new ScalaIntroduceVariableHandler();

      Assert.assertTrue(myFile instanceof ScalaFile);
      ScExpression selectedExpr = null;
      ScType[] types = null;
      if (ScalaRefactoringUtil.getExpression(getProject(), myEditor, myFile, startOffset, endOffset) instanceof Some) {
        Some temp = (Some) ScalaRefactoringUtil.getExpression(getProject(), myEditor, myFile, startOffset, endOffset);
        selectedExpr = IntroduceVariableTestUtil.extract1((Tuple2<ScExpression, ScType[]>) temp.get());
        types = IntroduceVariableTestUtil.extract2((Tuple2<ScExpression, ScType[]>) temp.get());
      }
      Assert.assertNotNull("Selected expression reference points to null", selectedExpr);

      TextRange[] occurences = ScalaRefactoringUtil.getOccurrenceRanges(ScalaRefactoringUtil.unparExpr(selectedExpr), myFile);
      String varName = "value";

      introduceVariableHandler.runRefactoring(startOffset, endOffset, myFile, myEditor, selectedExpr,
          occurences, varName, types[0], replaceAllOccurences, false);


      result = myEditor.getDocument().getText();
      //int caretOffset = myEditor.getCaretModel().getOffset();
      //result = result.substring(0, caretOffset) + TestUtils.CARET_MARKER + result.substring(caretOffset);
    } finally {
      fileEditorManager.closeFile(virtualFile);
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



  protected String removeAllMarker(String text) {
    int index = text.indexOf(ALL_MARKER);
    myOffset = index - 1;
    return text.substring(0, index) + text.substring(index + ALL_MARKER.length());
  }

}