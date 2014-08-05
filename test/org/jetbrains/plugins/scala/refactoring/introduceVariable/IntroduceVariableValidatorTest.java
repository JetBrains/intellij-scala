package org.jetbrains.plugins.scala.refactoring.introduceVariable;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.lang.actions.ActionTestBase;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaVariableValidator;
import org.jetbrains.plugins.scala.util.TestUtils;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * User: Alexander Podkhalyuzin
 * Date: 03.07.2008
 */


public class IntroduceVariableValidatorTest extends ActionTestBase {

  @NonNls
  private static final String DATA_PATH = "/introduceVariable/validator/data";

  protected static final String ALL_MARKER = "<all>";

  protected Editor myEditor;
  protected FileEditorManager fileEditorManager;
  protected String newDocumentText;
  protected PsiFile myFile;
  private boolean replaceAllOccurences;

  public IntroduceVariableValidatorTest() {
    super(System.getProperty("path") != null ?
        System.getProperty("path") :
        TestUtils.getTestDataPath() + DATA_PATH
    );
  }

  private String processFile(final PsiFile file) throws IncorrectOperationException, InvalidDataException, IOException {
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
    myEditor = fileEditorManager.openTextEditor(new OpenFileDescriptor(getProject(), myFile.getVirtualFile(), 0), false);

    try {

      String varName = "value";

      ScalaVariableValidator validator = IntroduceVariableTestUtil.getValidator(getProject(), myEditor,
          (ScalaFile) myFile, startOffset, endOffset);
      Set<String> set = new HashSet<String>();
      set.addAll(validator.isOKImpl(varName, replaceAllOccurences).values());
      for (String s: set) result += s + "\n";
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
    return result;
  }


  public static Test suite() {
    return new IntroduceVariableValidatorTest();
  }

  protected String removeAllMarker(String text) {
    int index = text.indexOf(ALL_MARKER);
    myOffset = index - 1;
    return text.substring(0, index) + text.substring(index + ALL_MARKER.length());
  }

}
