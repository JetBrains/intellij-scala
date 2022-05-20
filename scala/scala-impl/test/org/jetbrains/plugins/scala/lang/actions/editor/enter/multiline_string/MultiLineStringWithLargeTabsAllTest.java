package org.jetbrains.plugins.scala.lang.actions.editor.enter.multiline_string;

import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import junit.framework.Test;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.actions.editor.enter.AbstractEnterActionTestBase;
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

/**
 * User: Dmitry Naydanov
 * Date: 10/1/12
 */
@RunWith(AllTests.class)
public class MultiLineStringWithLargeTabsAllTest extends AbstractEnterActionTestBase {
  private static final String DATA_PATH = "/actions/editor/enter/multiLineStringData/withTabs/indentAndMargin/4tabs";

  public MultiLineStringWithLargeTabsAllTest() {
    super(DATA_PATH);
  }

  @Override
  protected void setSettings(@NotNull Project project) {
    super.setSettings(project);

    ScalaCodeStyleSettings scalaSettings = getScalaSettings(project);
    scalaSettings.MULTILINE_STRING_CLOSING_QUOTES_ON_NEW_LINE = true;
    scalaSettings.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER = true;
    scalaSettings.MULTILINE_STRING_MARGIN_INDENT = 2;

    getCommonSettings(project).ALIGN_MULTILINE_BINARY_OPERATION = true;
  }

  @Override
  protected void setIndentSettings(@NotNull CommonCodeStyleSettings.IndentOptions indentOptions) {
    indentOptions.USE_TAB_CHARACTER = true;
    indentOptions.TAB_SIZE = 4;
    indentOptions.INDENT_SIZE = 4;
    indentOptions.CONTINUATION_INDENT_SIZE = 4;
  }

  public static Test suite() {
    return new MultiLineStringWithLargeTabsAllTest();
  }
}
