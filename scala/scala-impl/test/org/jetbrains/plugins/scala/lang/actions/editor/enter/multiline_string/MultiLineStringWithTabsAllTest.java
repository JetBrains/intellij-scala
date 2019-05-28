package org.jetbrains.plugins.scala.lang.actions.editor.enter.multiline_string;

import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import junit.framework.Test;
import org.jetbrains.plugins.scala.lang.actions.editor.enter.AbstractEnterActionTestBase;
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

import static org.junit.Assert.assertNotNull;

/**
 * User: Dmitry Naydanov
 * Date: 10/1/12
 */
@RunWith(AllTests.class)
public class MultiLineStringWithTabsAllTest extends AbstractEnterActionTestBase {
  private static final String DATA_PATH = "/actions/editor/enter/multiLineStringData/withTabs/indentAndMargin/2tabs";
  
  public MultiLineStringWithTabsAllTest() {
      super(DATA_PATH);
  }

  @Override
  protected void setSettings() {
    super.setSettings();
    final CommonCodeStyleSettings settings = getCommonSettings();
    final ScalaCodeStyleSettings scalaSettings = settings.getRootSettings().getCustomSettings(ScalaCodeStyleSettings.class);

    scalaSettings.MULTILINE_STRING_CLOSING_QUOTES_ON_NEW_LINE = true;
    scalaSettings.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER = true;
    scalaSettings.MULTILINE_STRING_MARGIN_INDENT = 2;
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true;

    CommonCodeStyleSettings.IndentOptions indentOptions = settings.getIndentOptions();
    assertNotNull(indentOptions);
    indentOptions.USE_TAB_CHARACTER = true;
    indentOptions.TAB_SIZE = 2;
    indentOptions.INDENT_SIZE = 2;
    indentOptions.CONTINUATION_INDENT_SIZE = 2;
  }
  
  public static Test suite() {
    return new MultiLineStringWithTabsAllTest();
  }
}
