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
import org.jetbrains.plugins.scala.ScalaLanguage;
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings;
import org.jetbrains.plugins.scala.util.TestUtils;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

/**
 * Base class for java formatter tests that holds utility methods.
 *
 * @author Denis Zhdanov
 * @since Apr 27, 2010 6:26:29 PM
 */

//todo: almost duplicate from Java
public abstract class AbstractScalaFormatterTestBase extends LightIdeaTestCase {

  protected enum Action {REFORMAT, INDENT}

  private interface TestFormatAction {
    void run(PsiFile psiFile, int startOffset, int endOffset);
  }

  private static final Map<Action, TestFormatAction> ACTIONS = new EnumMap<Action, TestFormatAction>(Action.class);
  static {
    ACTIONS.put(Action.REFORMAT, new TestFormatAction() {
      public void run(PsiFile psiFile, int startOffset, int endOffset) {
        CodeStyleManager.getInstance(getProject()).reformatText(psiFile, startOffset, endOffset);
      }
    });
    ACTIONS.put(Action.INDENT, new TestFormatAction() {
      public void run(PsiFile psiFile, int startOffset, int endOffset) {
        CodeStyleManager.getInstance(getProject()).adjustLineIndent(psiFile, startOffset);
      }
    });
  }

  private static final String BASE_PATH = TestUtils.getTestDataPath() + "/psi/formatter";

  public TextRange myTextRange;
  public TextRange myLineRange;

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

  public void doTextTest(final String text, String textAfter) throws IncorrectOperationException {
    doTextTest(Action.REFORMAT, StringUtil.convertLineSeparators(text), StringUtil.convertLineSeparators(textAfter));
  }

  public void doTextTest(final Action action, final String text, String textAfter) throws IncorrectOperationException {
    final PsiFile file = createFile("A.scala", text);

    if (myLineRange != null) {
      final DocumentImpl document = new DocumentImpl(text);
      myTextRange =
        new TextRange(document.getLineStartOffset(myLineRange.getStartOffset()), document.getLineEndOffset(myLineRange.getEndOffset()));
    }

    /*
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            performFormatting(file);
          }
        });
      }
    }, null, null);

    assertEquals(prepareText(textAfter), prepareText(file.getText()));


    */

    final PsiDocumentManager manager = PsiDocumentManager.getInstance(getProject());
    final Document document = manager.getDocument(file);


    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            document.replaceString(0, document.getTextLength(), text);
            manager.commitDocument(document);
            try {
              TextRange rangeToUse = myTextRange;
              if (rangeToUse == null) {
                rangeToUse = file.getTextRange();
              }
              ACTIONS.get(action).run(file, rangeToUse.getStartOffset(), rangeToUse.getEndOffset());
            }
            catch (IncorrectOperationException e) {
              assertTrue(e.getLocalizedMessage(), false);
            }
          }
        });
      }
    }, "", "");


    if (document == null) {
      fail("Don't expect the document to be null");
      return;
    }
    assertEquals(prepareText(textAfter), prepareText(document.getText()));
    manager.commitDocument(document);
    assertEquals(prepareText(textAfter), prepareText(file.getText()));

  }

  //todo: was unused, should be deleted (??)
/*  public void doMethodTest(final String before, final String after) throws Exception {
    doTextTest(
      Action.REFORMAT,
      "class Foo{\n" + "    void foo() {\n" + before + '\n' + "    }\n" + "}",
      "class Foo {\n" + "    void foo() {\n" + shiftIndentInside(after, 8, false) + '\n' + "    }\n" + "}"
    );
  }

  public void doClassTest(final String before, final String after) throws Exception {
    doTextTest(
      Action.REFORMAT,
      "class Foo{\n" + before + '\n' + "}",
      "class Foo {\n" + shiftIndentInside(after, 4, false) + '\n' + "}"
    );
  }*/

  private static String prepareText(String actual) {
    if (actual.startsWith("\n")) {
      actual = actual.substring(1);
    }
    if (actual.startsWith("\n")) {
      actual = actual.substring(1);
    }

    // Strip trailing spaces
    final Document doc = EditorFactory.getInstance().createDocument(actual);
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            ((DocumentImpl)doc).stripTrailingSpaces(getProject());
          }
        });
      }
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
}

