package org.jetbrains.plugins.scala.refactoring.introduceVariable;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.scala.lang.actions.ActionTestBase;
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression;
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses;
import org.jetbrains.plugins.scala.lang.psi.types.ScType;
import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.OccurrenceData;
import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.ScalaIntroduceVariableHandler;
import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.ScopeItem;
import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.ScopeSuggester;
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings;
import org.junit.Assert;
import scala.Option;
import scala.Tuple2;

import java.io.IOException;

/**
 * User: Alexander Podkhalyuzin
 * Date: 02.07.2008
 */


abstract public class AbstractIntroduceVariableTestBase extends ActionTestBase {

  protected static final String ALL_MARKER = "<all>";
  protected static final String COMPANION_MARKER = "<companion>";
  protected static final String INHERITORS_MARKER = "<inheritors>";

  protected Editor myEditor;
  protected FileEditorManager fileEditorManager;
  protected String newDocumentText;
  protected PsiFile myFile;


  protected boolean replaceAllOccurences = false;
  protected boolean replaceCompanionObjOccurrences = false;
  protected boolean replaceOccurrencesFromInheritors = false;

  public AbstractIntroduceVariableTestBase(String DATA_PATH) {
    super(System.getProperty("path") != null ?
            System.getProperty("path") :
            DATA_PATH
    );
    replaceAllOccurences = System.getProperty("replaceAll") != null &&
            Boolean.parseBoolean(System.getProperty("path"));
    replaceCompanionObjOccurrences = System.getProperty("replaceCompanion") != null &&
            Boolean.parseBoolean(System.getProperty("path"));
    replaceOccurrencesFromInheritors = System.getProperty("replaceInheritors") != null &&
            Boolean.parseBoolean(System.getProperty("path"));
  }


  private String getName(String fileText) {
    if (!(fileText.indexOf("//") == 0)) {
      junit.framework.Assert.assertTrue("Typename to validator should be in first comment statement.", false);
    }
    return fileText.substring(2, fileText.indexOf("\n")).replaceAll("\\W", "");
  }

  private String removeTypenameComment(String fileText) {
    int begin = fileText.indexOf("//");
    return fileText.substring(0, begin) + fileText.substring(fileText.indexOf("\n", begin) + 1);
  }

  private String processFile(final PsiFile file) throws IncorrectOperationException, InvalidDataException, IOException {
    Project project = getProject();
    Object oldSettings = ScalaCodeStyleSettings.getInstance(getProject()).clone();

    TypeAnnotationSettings.set(getProject(), TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProject())));

    final SyntheticClasses syntheticClasses = project.getComponent(SyntheticClasses.class);
    if (!syntheticClasses.isClassesRegistered()) {
      syntheticClasses.registerClasses();
    }
    String result = "";
    String fileText = file.getText();


    int startOffset = fileText.indexOf(TestUtils.BEGIN_MARKER);
    if (startOffset >= 0) {
      replaceAllOccurences = false;
      fileText = TestUtils.removeBeginMarker(fileText);
    } else {
      startOffset = fileText.indexOf(COMPANION_MARKER);
      if (startOffset >= 0) {
        replaceCompanionObjOccurrences = true;
        fileText = removeMarker(fileText, COMPANION_MARKER);
      } else {
        startOffset = fileText.indexOf(INHERITORS_MARKER);
        if (startOffset >= 0) {
          replaceOccurrencesFromInheritors = true;
          fileText = removeMarker(fileText, INHERITORS_MARKER);
        } else {
          startOffset = fileText.indexOf(ALL_MARKER);
          if (startOffset >= 0) {
            replaceAllOccurences = true;
            fileText = removeMarker(fileText, ALL_MARKER);
          }
        }
      }
    }

    int endOffset = fileText.indexOf(TestUtils.END_MARKER);
    fileText = TestUtils.removeEndMarker(fileText);
    myFile = TestUtils.createPseudoPhysicalScalaFile(project, fileText);
    fileEditorManager = FileEditorManager.getInstance(project);
    VirtualFile virtualFile = myFile.getVirtualFile();
    assert virtualFile != null;
    myEditor = fileEditorManager.openTextEditor(new OpenFileDescriptor(project, virtualFile, 0), false);
    assert myEditor != null;

    try {
      myEditor.getSelectionModel().setSelection(startOffset, endOffset);

      // gathering data for introduce variable
      ScalaIntroduceVariableHandler introduceVariableHandler = new ScalaIntroduceVariableHandler();

      Assert.assertTrue(myFile instanceof ScalaFile);
      PsiElement element = PsiTreeUtil.getParentOfType(myFile.findElementAt(startOffset), ScExpression.class, ScTypeElement.class);
      if (element instanceof ScExpression){
        ScExpression selectedExpr = null;
        ScType[] types = null;

        Option<Tuple2<ScExpression, ScType[]>> maybeExpression =
                ScalaRefactoringUtil.getExpressionWithTypes(myFile, startOffset, endOffset, project, myEditor);
        if (maybeExpression.isDefined()) {
          Tuple2<ScExpression, ScType[]> tuple2 = maybeExpression.get();
          selectedExpr = tuple2._1();
          types = tuple2._2();
        }
        Assert.assertNotNull("Selected expression reference points to null", selectedExpr);

        TextRange[] occurrences = ScalaRefactoringUtil.getOccurrenceRanges(ScalaRefactoringUtil.unparExpr(selectedExpr), myFile);
        String varName = "value";

        introduceVariableHandler.runRefactoring(startOffset, endOffset, myFile, selectedExpr,
                occurrences, varName, types[0], replaceAllOccurences, false, myEditor);

        result = myEditor.getDocument().getText();
      } else if (element instanceof ScTypeElement){
        Option<ScTypeElement> optionType = ScalaRefactoringUtil.getTypeElement(myFile, startOffset, endOffset);
        if (optionType.isEmpty()){
          result = "Selected block should be presented as type element";
        } else {
          ScTypeElement typeElement = optionType.get();
          String typeName = getName(fileText);

          ScopeItem[] scopes = ScopeSuggester.suggestScopes(introduceVariableHandler, project, myEditor, myFile, typeElement);

//          if (replaceOccurrencesFromInheritors) {
//            ScTypeDefinition classOrTrait = PsiTreeUtil.getParentOfType(scopes.get(0).fileEncloser(), ScClass.class, ScTrait.class);
//            System.out.println(classOrTrait == null);
//            if (classOrTrait != null) {
//              Tuple2<ScTypeElement[], ScalaTypeValidator[]> inheritors =
//                ScalaRefactoringUtil.getOccurrencesInInheritors(typeElement, classOrTrait,
//                      introduceVariableHandler, getProject(), myEditor);
//
//              scopes.get(0).setInheretedOccurrences(inheritors._1());
//            }
//          }

          OccurrenceData occurrences = OccurrenceData.apply(typeElement, replaceAllOccurences,
                  replaceCompanionObjOccurrences, replaceOccurrencesFromInheritors, scopes[0]);

          introduceVariableHandler.runRefactoringForTypes(myFile, myEditor, typeElement,
                  typeName, occurrences, scopes[0]);

          result = removeTypenameComment(myEditor.getDocument().getText());
        }
      } else {
        Assert.assertTrue("Element should be typeElement or Expression", false);
      }

      //int caretOffset = myEditor.getCaretModel().getOffset();
      //result = result.substring(0, caretOffset) + TestUtils.CARET_MARKER + result.substring(caretOffset);
    } finally {
      fileEditorManager.closeFile(virtualFile);
      myEditor = null;
    }

    TypeAnnotationSettings.set(getProject(), ((ScalaCodeStyleSettings) oldSettings));
    return result;
  }


  public String transform(String testName, String[] data) throws Exception {
    setSettings();
    String fileText = data[0];
    final PsiFile psiFile = TestUtils.createPseudoPhysicalScalaFile(getProject(), fileText);

    return processFile(psiFile);
  }

  protected String removeMarker(String text, String marker) {
    int index = text.indexOf(marker);
    myOffset = index - 1;
    return text.substring(0, index) + text.substring(index + marker.length());
  }
}