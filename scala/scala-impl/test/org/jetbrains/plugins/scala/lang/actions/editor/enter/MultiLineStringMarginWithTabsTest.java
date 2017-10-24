package org.jetbrains.plugins.scala.lang.actions.editor.enter;

import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import junit.framework.Test;
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

/**
 * User: Dmitry Naydanov
 * Date: 10/1/12
 */
@RunWith(AllTests.class)
public class MultiLineStringMarginWithTabsTest extends AbstractEnterActionTestBase {
  private static final String DATA_PATH = "/actions/editor/enter/multiLineStringData/withTabs/indentAndMargin/2tabs";
  
  public MultiLineStringMarginWithTabsTest() {
      super(TestUtils.getTestDataPath() + DATA_PATH);
  }

  @Override
  protected void setSettings() {
    super.setSettings();
    final CommonCodeStyleSettings settings = getSettings();
    final ScalaCodeStyleSettings scalaSettings = settings.getRootSettings().getCustomSettings(ScalaCodeStyleSettings.class);

    scalaSettings.MULTILINE_STRING_SUPORT = ScalaCodeStyleSettings.MULTILINE_STRING_ALL;
    scalaSettings.MULTI_LINE_STRING_MARGIN_INDENT = 2;
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true;
    settings.getIndentOptions().USE_TAB_CHARACTER = true;
    settings.getIndentOptions().TAB_SIZE = 2;
    settings.getIndentOptions().INDENT_SIZE = 2;
    settings.getIndentOptions().CONTINUATION_INDENT_SIZE = 2;
  }
  
  public static Test suite() {
    return new MultiLineStringMarginWithTabsTest();
  }
}
