package org.jetbrains.plugins.scala.lang.parser.stress;

import org.jetbrains.plugins.scala.Console;
import com.intellij.lang.Language;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.Assert;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer;
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes;
import org.jetbrains.plugins.scala.lang.parser.ScalaParser;
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * @author ilyas
 */
public class DragSearchTest extends BaseScalaFileSetTestCase {
  protected static final int MAX_ROLLBACKS = 30;

  @NonNls
  private static final String DATA_PATH = "test/org/jetbrains/plugins/scala/lang/parser/stress/data/";
  protected static final String TEST_FILE_PATTERN = "(.*)\\.test";


  public DragSearchTest() {
    super(System.getProperty("path") != null ?
            System.getProperty("path") :
            DATA_PATH
    );
  }

  public String transform(String testName, String[] data) throws Exception {
    return null;
  }

  protected void runTest(final File myTestFile) throws Throwable {

    String content = new String(FileUtil.loadFileText(myTestFile));
    String testName = myTestFile.getName();
    Assert.assertNotNull(content);

    content = StringUtil.replace(content, "\r", ""); // for MACs
    transform(testName, content);

//    Assert.assertFalse(testName, transformed.contains("PsiErrorElement"));
  }

  public String getSearchPattern() {
    return TEST_FILE_PATTERN;
  }


  public String transform(String testName, String fileText) throws Exception {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
    PsiElementFactory psiElementFactory = facade.getElementFactory();
    Assert.assertNotNull(psiElementFactory);
    Assert.assertNotNull(TEMP_FILE);
    Assert.assertNotNull(fileText);

    Language language = ScalaFileType.SCALA_LANGUAGE;
    PsiBuilder psiBuilder = PsiBuilderFactory.getInstance().createBuilder(new ScalaLexer(), language, fileText);
    DragBuilderWrapper dragBuilder = new DragBuilderWrapper(myProject, psiBuilder);
    new ScalaParser().parse(ScalaElementTypes.FILE(), dragBuilder);

    Pair<TextRange, Integer>[] dragInfo = dragBuilder.getDragInfo();
    exploreForDrags(dragInfo, testName, fileText);

    PsiFile psiFile = PsiFileFactory.getInstance(myProject).createFileFromText(TEMP_FILE, fileText);
    return DebugUtil.psiToString(psiFile, false);
  }

  private static void exploreForDrags(Pair<TextRange, Integer>[] dragInfo, String testName, String fileText) {
    int ourMaximum = max(dragInfo);
    List<Pair<TextRange, Integer>> penals = ContainerUtil.findAll(dragInfo, new Condition<Pair<TextRange, Integer>>() {
      public boolean value(Pair<TextRange, Integer> pair) {
        return pair.getSecond() >= MAX_ROLLBACKS;
      }
    });

    if (penals.size() > 0) {
      Console.println("[" + testName + "] Max rollbacks: " + ourMaximum);
      Console.println(" Look for example @: " + stickRanges(penals.toArray(new Pair[penals.size()]), fileText));
      Assert.assertTrue("Too much rollbacks: " + ourMaximum, ourMaximum < MAX_ROLLBACKS);
    }

  }

  private static int max(Pair<TextRange, Integer>[] dragInfo) {
    int max = 0;
    for (Pair<TextRange, Integer> pair : dragInfo) {
      if (pair.getSecond() > max) {
        max = pair.getSecond();
      }
    }
    return max;
  }

  private static String stickRanges(Pair<TextRange, Integer>[] infos, String fileText) {
    Arrays.sort(infos, new DragStorage.RangeComparator());
    StringBuffer buffer = new StringBuffer();
    for (Pair<TextRange, Integer> info : infos) {
      TextRange range = info.getFirst();
      int start = range.getStartOffset();
      int end = range.getEndOffset();
      buffer.append(end < fileText.length() ? fileText.substring(range.getStartOffset(), end) : fileText.substring(start));
      buffer.append("\n");
    }

    return buffer.toString();
  }

  public static Test suite() {
    return new DragSearchTest();
  }


}
