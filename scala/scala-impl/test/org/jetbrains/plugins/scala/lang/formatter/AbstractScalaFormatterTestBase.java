package org.jetbrains.plugins.scala.lang.formatter;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaLanguage;
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings;
import org.jetbrains.plugins.scala.util.TestUtils;

import java.io.File;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Base class for java formatter tests that holds utility methods.
 *
 * @author Denis Zhdanov
 * @since Apr 27, 2010 6:26:29 PM
 */
// NOTE: initially was almost duplicate from Java
public abstract class AbstractScalaFormatterTestBase extends LightIdeaTestCase {
  private static final String TempFileName = "A.scala";

  protected enum Action {REFORMAT, INDENT}

  private interface TestFormatAction {
    void run(PsiFile psiFile, int startOffset, int endOffset);
    void run(PsiFile psiFile, List<TextRange> formatRanges);
  }

  private static final Map<Action, TestFormatAction> ACTIONS = new EnumMap<Action, TestFormatAction>(Action.class);
  static {
    ACTIONS.put(Action.REFORMAT, new TestFormatAction() {
      public void run(PsiFile psiFile, int startOffset, int endOffset) {
        CodeStyleManager.getInstance(getProject()).reformatText(psiFile, startOffset, endOffset);
      }

      @Override
      public void run(PsiFile psiFile, List<TextRange> formatRanges) {
        CodeStyleManager.getInstance(getProject()).reformatText(psiFile, formatRanges);
      }
    });
    ACTIONS.put(Action.INDENT, new TestFormatAction() {
      public void run(PsiFile psiFile, int startOffset, int endOffset) {
        CodeStyleManager.getInstance(getProject()).adjustLineIndent(psiFile, startOffset);
      }

      @Override
      public void run(PsiFile psiFile, List<TextRange> formatRanges) {
        throw new UnsupportedOperationException("Adjusting indents for a collection of ranges is not supported in tests.");
      }
    });
  }

  private static final String BASE_PATH = TestUtils.getTestDataPath() + "/psi/formatter";

  public List<TextRange> myTextRanges = new LinkedList<TextRange>();

  public CommonCodeStyleSettings getCommonSettings() {
      return getSettings().getCommonSettings(ScalaLanguage.INSTANCE);
  }

  public ScalaCodeStyleSettings getScalaSettings() {
    return getSettings().getCustomSettings(ScalaCodeStyleSettings.class);
  }

  public CodeStyleSettings getSettings() {
    return CodeStyleSettingsManager.getSettings(getProject());
  }

  public CommonCodeStyleSettings.IndentOptions getIndentOptions() {
    return getCommonSettings().getIndentOptions();
  }

  public void doTest() throws Exception {
    doTest(getTestName(false) + ".scala", getTestName(false) + "_after.scala");
  }

  public void doTest(String fileNameBefore, String fileNameAfter) throws Exception {
    doTextTest(Action.REFORMAT, loadFile(fileNameBefore), loadFile(fileNameAfter));
  }

  public void doTextTest(final String text, final String textAfter) throws IncorrectOperationException {
    doTextTest(text, textAfter, 1);
  }

  public void doTextTest(final String text, final String textAfter, final int repeats) throws IncorrectOperationException {
    doTextTest(Action.REFORMAT, StringUtil.convertLineSeparators(text), StringUtil.convertLineSeparators(textAfter), TempFileName, repeats);
  }
  
  public void doTextTest(final String text, final String textAfter, final String fileName) throws IncorrectOperationException {
    doTextTest(Action.REFORMAT, StringUtil.convertLineSeparators(text), StringUtil.convertLineSeparators(textAfter), fileName, 1);
  }

  public void doTextTest(String value) {
    doTextTest(value, value);
  }

  public void doTextTest(String value, int actionRepeats) {
    doTextTest(value, value, actionRepeats);
  }

  private void doTextTest(final Action action, final String text, final String textAfter) throws IncorrectOperationException {
    doTextTest(action, text, textAfter, TempFileName, 1);
  }

  private void doTextTest(final Action action, final String text, final String textAfter, final String fileName, int actionRepeats) throws IncorrectOperationException {
    assertTrue("action should be applied at least once", actionRepeats >= 1);
    if(actionRepeats > 1 && !myTextRanges.isEmpty()) {
      fail("for now an action can not be applied multiple times for selection");
    }

    final PsiFile file = createFile(fileName, text);

    final PsiDocumentManager manager = PsiDocumentManager.getInstance(getProject());
    final Document document = manager.getDocument(file);

    if (document == null) {
      fail("Don't expect the document to be null");
    }

    runCommandInWriteAction(() -> {
      document.replaceString(0, document.getTextLength(), text);
      manager.commitDocument(document);
      for (int actionNumber = 0; actionNumber < actionRepeats; actionNumber++) {
        try {
          if (myTextRanges.size() > 1) {
            ACTIONS.get(action).run(file, myTextRanges);
          } else {
            TextRange rangeToUse = myTextRanges.isEmpty() ? file.getTextRange() : myTextRanges.get(0);
            ACTIONS.get(action).run(file, rangeToUse.getStartOffset(), rangeToUse.getEndOffset());
          }
        }
        catch (IncorrectOperationException e) {
          fail(e.getLocalizedMessage());
        }
      }
    }, "", "");

    assertEquals(prepareText(textAfter), prepareText(document.getText()));
    manager.commitDocument(document);
    assertEquals(prepareText(textAfter), prepareText(file.getText()));
  }

  @NotNull
  private static String prepareText(@NotNull String actual) {
    if (actual.startsWith("\n")) {
      actual = actual.substring(1);
    }
    if (actual.startsWith("\n")) {
      actual = actual.substring(1);
    }

    // Strip trailing spaces
    final Document doc = EditorFactory.getInstance().createDocument(actual);
    runCommandInWriteAction(() -> {
      ((DocumentImpl) doc).stripTrailingSpaces(getProject());
    }, "formatting", null);
    return doc.getText().trim();
  }

  private static String loadFile(String name) throws Exception {
    String fullName = BASE_PATH + File.separatorChar + name;
    String text = new String(FileUtil.loadFileText(new File(fullName)));
    text = StringUtil.convertLineSeparators(text);
    return text;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    TestUtils.disableTimerThread();
  }

  private static void runCommandInWriteAction(final Runnable runnable,
                                       final String name,
                                       final String groupId) {
    Runnable writableRunnable = () -> ApplicationManager.getApplication().runWriteAction(runnable);;
    CommandProcessor.getInstance().executeCommand(getProject(), writableRunnable, name, groupId);
  }
}

