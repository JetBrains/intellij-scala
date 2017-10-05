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

/**
 * User: Dmitry Naydanov
 * Date: 4/16/12
 */
@RunWith(AllTests.class)
public class MultiLineStringFormatterTest extends FormatterTest {
  private final static String DATA_PATH = "/formatter/multiLineStringData/";

  public static Test suite() throws IOException {
    return new MultiLineStringFormatterTest();
  }

  public MultiLineStringFormatterTest() throws IOException {
    super((new File(TestUtils.getTestDataPath() + DATA_PATH)).getCanonicalPath());
  }

  @Override
  protected void setUp(Project project) {
    super.setUp(project);
    CommonCodeStyleSettings settings = getSettings();
    ScalaCodeStyleSettings scalaSettings = settings.getRootSettings().getCustomSettings(ScalaCodeStyleSettings.class);

    scalaSettings.MULTILINE_STRING_SUPORT = ScalaCodeStyleSettings.MULTILINE_STRING_ALL;
    scalaSettings.KEEP_MULTI_LINE_QUOTES = false;
    scalaSettings.MULTI_LINE_QUOTES_ON_NEW_LINE = true;
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true;
    scalaSettings.MULTI_LINE_STRING_MARGIN_INDENT = 3;
  }
}
