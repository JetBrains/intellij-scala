package org.jetbrains.plugins.scala.lang.parser.stress;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.openapi.project.Project;
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
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer;
import org.jetbrains.plugins.scala.lang.parser.ScalaParser;
import org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition;
import org.jetbrains.plugins.scala.project.package$;
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

import java.io.File;
import java.util.List;

/**
 * @author ilyas
 */
@RunWith(AllTests.class)
public class DragSearchTest extends BaseScalaFileSetTestCase {
  protected static final int MAX_ROLLBACKS = 30;

  @NonNls
  private static final String DATA_PATH = "/parser/stress/data/";
  protected static final String TEST_FILE_PATTERN = "(.*)\\.test";


  public DragSearchTest() {
    super(System.getProperty("path") != null ?
            System.getProperty("path") :
        TestUtils.getTestDataPath() + DATA_PATH
    );
  }

  public String transform(String testName, String[] data) throws Exception {
    return null;
  }

  protected void runTest(final File myTestFile) throws Throwable {

    String content = new String(FileUtil.loadFileText(myTestFile));
    Assert.assertNotNull(content);

    content = StringUtil.replace(content, "\r", ""); // for MACs
    transform(content);

//    Assert.assertFalse(testName, transformed.contains("PsiErrorElement"));
  }

  public String getSearchPattern() {
    return TEST_FILE_PATTERN;
  }


  public String transform(String fileText) throws Exception {
    Project project = getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiElementFactory psiElementFactory = facade.getElementFactory();
    Assert.assertNotNull(psiElementFactory);
    Assert.assertNotNull(TEMP_FILE);
    Assert.assertNotNull(fileText);

    PsiBuilder psiBuilder = PsiBuilderFactory.getInstance().createBuilder(new ScalaParserDefinition(), new ScalaLexer(), fileText);
    DragBuilderWrapper dragBuilder = new DragBuilderWrapper(project, psiBuilder);
    new ScalaParser().parse(package$.MODULE$.elementTypesIn(project).file(), dragBuilder);

    Pair<TextRange, Integer>[] dragInfo = dragBuilder.getDragInfo();
    exploreForDrags(dragInfo);

    PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText(TEMP_FILE,
        ScalaFileType.SCALA_FILE_TYPE, fileText);
    return DebugUtil.psiToString(psiFile, false);
  }

  private static void exploreForDrags(Pair<TextRange, Integer>[] dragInfo) {
    int ourMaximum = max(dragInfo);
    List<Pair<TextRange, Integer>> penals = ContainerUtil.findAll(dragInfo, new Condition<Pair<TextRange, Integer>>() {
      public boolean value(Pair<TextRange, Integer> pair) {
        return pair.getSecond() >= MAX_ROLLBACKS;
      }
    });

    if (penals.size() > 0) {
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

  public static Test suite() {
    return new DragSearchTest();
  }


}
