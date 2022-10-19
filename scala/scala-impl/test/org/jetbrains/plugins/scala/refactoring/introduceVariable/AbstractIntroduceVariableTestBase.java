package org.jetbrains.plugins.scala.refactoring.introduceVariable;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.actions.ActionTestBase;
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses;
import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.IntroduceTypeAlias;
import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.ScalaIntroduceVariableHandler;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings;
import org.junit.Assert;
import scala.Option;
import scala.Tuple2;

// TODO: rewrite this whole class
abstract public class AbstractIntroduceVariableTestBase extends ActionTestBase {

  protected static final String ALL_MARKER = "<all>";
  protected static final String COMPANION_MARKER = "<companion>";
  protected static final String INHERITORS_MARKER = "<inheritors>";

  protected Editor myEditor;
  protected FileEditorManager fileEditorManager;
  protected PsiFile myFile;

  protected boolean replaceAllOccurrences = false;
  protected boolean replaceCompanionObjOccurrences = false;
  protected boolean replaceOccurrencesFromInheritors = false;

  public AbstractIntroduceVariableTestBase(@NotNull @NonNls String path) {
    super(path);
  }

  private Tuple2<String, Option<String>> extractNameFromLeadingComment(String fileText) {
    String nameCommentPrefix = "//name=";
    int lineCommentIndex = fileText.indexOf(nameCommentPrefix);
    if (lineCommentIndex != 0)
      return Tuple2.apply(fileText, Option.empty());
    else  {
      int newLineIndex = fileText.indexOf("\n");
      String commentContent = fileText.substring(nameCommentPrefix.length(), newLineIndex);
      String contentAfterComment = fileText.substring(newLineIndex + 1);;

      String suggestedName = commentContent.replaceAll("\\W", "");
      return Tuple2.apply(contentAfterComment, Option.apply(suggestedName));
    }
  }

  @SuppressWarnings({"UnnecessaryLocalVariable"})
  @NotNull
  private String processFile(@NotNull PsiFile file,
                             @NotNull Project project) throws IncorrectOperationException, InvalidDataException {
    Object oldSettings = ScalaCodeStyleSettings.getInstance(project).clone();

    TypeAnnotationSettings.set(project, TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(project)));

    final SyntheticClasses syntheticClasses = SyntheticClasses.get(project);
    if (!syntheticClasses.isClassesRegistered()) {
      syntheticClasses.registerClasses();
    }
    String result = "";
    final String fileTextOriginal = file.getText();
    Tuple2<String, Option<String>> fileTextAndSuggestedName = extractNameFromLeadingComment(fileTextOriginal);
    String fileText = fileTextAndSuggestedName._1;
    Option<String> suggestedName = fileTextAndSuggestedName._2;

    int startOffset = fileText.indexOf(TestUtils.BEGIN_MARKER());
    int endOffset = -1;
    if (startOffset >= 0) {
      replaceAllOccurrences = false;
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
            replaceAllOccurrences = true;
            fileText = removeMarker(fileText, ALL_MARKER);
          } else {
            startOffset = fileText.indexOf(TestUtils.CARET_MARKER());
            if (startOffset >= 0) {
              replaceAllOccurrences = false;
              fileText = removeMarker(fileText, TestUtils.CARET_MARKER());
              endOffset = startOffset;
            }
          }
        }
      }
    }

    if (endOffset < 0) {
      endOffset = fileText.indexOf(TestUtils.END_MARKER());
      fileText = TestUtils.removeEndMarker(fileText);
    }
    myFile = createLightFile(fileText, project);
    Assert.assertTrue(myFile instanceof ScalaFile);

    fileEditorManager = FileEditorManager.getInstance(project);
    VirtualFile virtualFile = myFile.getVirtualFile();
    assert virtualFile != null;
    myEditor = fileEditorManager.openTextEditor(new OpenFileDescriptor(project, virtualFile, 0), false);
    assert myEditor != null;

    try {
      boolean justCaret = startOffset == endOffset;
      if (justCaret) {
        myEditor.getCaretModel().moveToOffset(startOffset);
      } else {
        myEditor.getSelectionModel().setSelection(startOffset, endOffset);
      }

      Document document = myEditor.getDocument();

      // gathering data for introduce variable
      ScalaIntroduceVariableHandler introduceVariableHandler = new ScalaIntroduceVariableHandler();

      var dataContextBuilder = SimpleDataContext.builder();
      if (!justCaret) {
        String name = suggestedName.getOrElse(() -> "value");
        //NOTE: nothing special in this magic
        //test data was written in the way that if we don't have a selection then we use the default variable name
        //It can be improved (e.g. by using special tags in test data) but there was no need for that yet
        dataContextBuilder.add(ScalaIntroduceVariableHandler.ForcedDefinitionNameDataKey(), name);
      }
      dataContextBuilder.add(ScalaIntroduceVariableHandler.ForcedReplaceCompanionObjOccurrencesKey(), replaceCompanionObjOccurrences);
      dataContextBuilder.add(ScalaIntroduceVariableHandler.ForcedReplaceAllOccurrencesKey(), replaceAllOccurrences);
      dataContextBuilder.add(IntroduceTypeAlias.ForcedReplaceOccurrenceInInheritors(), replaceOccurrencesFromInheritors);
      DataContext dataContext = dataContextBuilder.build();

      RefactoringActionHandler handler = introduceVariableHandler;
      try {
        handler.invoke(project, myEditor, myFile, dataContext);
        result = document.getText();
      } catch (CommonRefactoringUtil.RefactoringErrorHintException refactoringErrorHintException) {
        result = refactoringErrorHintException.getMessage();
      }
    } finally {
      fileEditorManager.closeFile(virtualFile);
      myEditor = null;
    }

    TypeAnnotationSettings.set(project, ((ScalaCodeStyleSettings) oldSettings));
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

  private String removeMarker(String text, String marker) {
    int index = text.indexOf(marker);
    myOffset = index - 1;
    return text.substring(0, index) + text.substring(index + marker.length());
  }
}