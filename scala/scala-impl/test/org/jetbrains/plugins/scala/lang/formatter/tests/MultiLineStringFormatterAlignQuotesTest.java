package org.jetbrains.plugins.scala.lang.formatter.tests;

import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import junit.framework.Test;
import org.jetbrains.plugins.scala.lang.formatter.FormatterTest;
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

import java.io.File;
import java.io.IOException;

@RunWith(AllTests.class)
public class MultiLineStringFormatterAlignQuotesTest extends FormatterTest {
  private final static String DATA_PATH = "/formatter/multiLineStringDataAlignQuotes/";

  public static Test suite() throws IOException {
    return new MultiLineStringFormatterAlignQuotesTest();
  }

  public MultiLineStringFormatterAlignQuotesTest() throws IOException {
    super((new File(TestUtils.getTestDataPath() + DATA_PATH)).getCanonicalPath());
  }

  @Override
  public void setUp(Project project) {
    super.setUp(project);
    CommonCodeStyleSettings settings = getCommonSettings();
    ScalaCodeStyleSettings scalaSettings = settings.getRootSettings().getCustomSettings(ScalaCodeStyleSettings.class);

    scalaSettings.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER = true;
    scalaSettings.MULTILINE_STRING_ALIGN_DANGLING_CLOSING_QUOTES = true;
    scalaSettings.MULTILINE_STRING_OPENING_QUOTES_ON_NEW_LINE = true;
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true;
    scalaSettings.MULTILINE_STRING_MARGIN_INDENT = 3;
  }
}
