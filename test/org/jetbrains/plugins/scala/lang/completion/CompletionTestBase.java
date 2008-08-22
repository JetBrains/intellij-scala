package org.jetbrains.plugins.scala.lang.completion;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.completion.CompletionData;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.CompletionVariant;
import com.intellij.codeInsight.completion.actions.CodeCompletionAction;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.scala.lang.actions.ActionTestBase;
import org.jetbrains.plugins.scala.util.TestUtils;

import java.io.IOException;
import java.util.*;

/**
 * @author Alexander Podkhalyuzin
 */
public abstract class CompletionTestBase extends ActionTestBase {
  protected Editor myEditor;
  protected FileEditorManager myFileEditorManager;
  protected PsiFile myFile;
  protected int myOffset;

  public CompletionTestBase(String path) {
    super(path);
  }

  protected CodeInsightActionHandler getCompetionHandler() {
    CodeCompletionAction action = new CodeCompletionAction();
    return action.getHandler();
  }

  protected String processFile(final PsiFile file) throws IncorrectOperationException, InvalidDataException, IOException {
    String result = "";
    String fileText = file.getText();
    int offset = fileText.indexOf(CARET_MARKER);
    fileText = removeMarker(fileText);
    myFile = createFile(fileText);
    myFileEditorManager = FileEditorManager.getInstance(myProject);
    myEditor = myFileEditorManager.openTextEditor(new OpenFileDescriptor(myProject, myFile.getVirtualFile(), 0), false);
    myEditor.getCaretModel().moveToOffset(offset);
    myOffset = myEditor.getCaretModel().getOffset();

    final CodeInsightActionHandler handler = getCompetionHandler();
    CompletionData data = CompletionUtil.getCompletionDataByElement(myFile);
    LookupElement[] items = getAcceptableItems(data);

    try {
      /*performAction(project, new Runnable() {
        public void run() {
          handler.invoke(project, myEditor, myFile);
        }
      });*/

      offset = myEditor.getCaretModel().getOffset();

      if (items.length > 0) {
        Arrays.sort(items);
        result = "";
        for (LookupElement item : items) {
          result = result + "\n" + item.getLookupString();
        }
        result = result.trim();
      }

    } finally {
      myFileEditorManager.closeFile(myFile.getVirtualFile());
      myEditor = null;
    }
    return result;
  }

  protected PsiFile createFile(String fileText) throws IncorrectOperationException {
    return TestUtils.createPseudoPhysicalScalaFile(myProject, fileText);
  }

  protected abstract LookupElement[] getAcceptableItems(CompletionData data) throws IncorrectOperationException;


  /**
   * retrurns acceptable variant for this completion
   *
   * @param completionData
   * @return
   */
  protected LookupElement[] getAcceptableItemsImpl(CompletionData completionData) throws IncorrectOperationException {

    final Set<LookupElement> lookupSet = new LinkedHashSet<LookupElement>();
    final PsiElement elem = myFile.findElementAt(myOffset);

    /**
     * Create fake file with dummy element
     */
    String newFileText = myFile.getText().substring(0, myOffset) + "IntellijIdeaRulezzz" +
            myFile.getText().substring(myOffset);
    /**
     * Hack for IDEA completion
     */
    PsiFile newFile = createFile(newFileText);
    PsiElement insertedElement = newFile.findElementAt(myOffset + 1);
    String prefix = newFile.getText().substring(insertedElement.getTextRange().getStartOffset(), myOffset);
    if (lookupSet.size() == 0) {
      final PsiReference ref = newFile.findReferenceAt(myOffset + 1);
      if (addKeywords(ref)) {
        final Set<CompletionVariant> keywordVariants = new HashSet<CompletionVariant>();
        completionData.addKeywordVariants(keywordVariants, insertedElement, newFile);
        completionData.completeKeywordsBySet(lookupSet, keywordVariants, insertedElement, new CamelHumpMatcher(prefix), newFile);
      }
      //todo: add variant for reference completion
    }

    ArrayList<LookupElement> lookupItems = new ArrayList<LookupElement>();
    final LookupElement[] items = lookupSet.toArray(new LookupElement[lookupSet.size()]);
    for (LookupElement item : items) if (item.getLookupString().startsWith(prefix)){
      lookupItems.add(item);
    }

    return lookupItems.toArray(new LookupElement[lookupItems.size()]);

  }

  public String transform(String testName, String[] data) throws Exception {
    setSettings();
    String fileText = data[0];
    final PsiFile psiFile = createFile(fileText);
    String result = processFile(psiFile);
    System.out.println("------------------------ " + testName + " ------------------------");
    System.out.println(result);
    System.out.println("");
    return result;
  }

  protected abstract boolean addKeywords(PsiReference ref);

  protected abstract boolean addReferenceVariants(PsiReference ref);
}
