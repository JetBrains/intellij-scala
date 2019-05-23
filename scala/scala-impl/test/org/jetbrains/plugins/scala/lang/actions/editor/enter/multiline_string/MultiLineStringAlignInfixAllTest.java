package org.jetbrains.plugins.scala.lang.actions.editor.enter.multiline_string;

import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import junit.framework.Test;
import org.jetbrains.plugins.scala.lang.actions.editor.enter.AbstractEnterActionTestBase;
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

@RunWith(AllTests.class)
public class MultiLineStringAlignInfixAllTest extends AbstractEnterActionTestBase {
  public static final String DATA_PATH = "/actions/editor/enter/multiLineStringData/alignInfix/indentAndMargin";

  public MultiLineStringAlignInfixAllTest() {
    super(DATA_PATH);
  }

  @Override
  protected void setSettings() {
    super.setSettings();
    final CommonCodeStyleSettings settings = getCommonSettings();
    final ScalaCodeStyleSettings scalaSettings = settings.getRootSettings().getCustomSettings(ScalaCodeStyleSettings.class);

    scalaSettings.MULTILINE_STRING_SUPPORT = ScalaCodeStyleSettings.MULTILINE_STRING_ALL;
    scalaSettings.MULTI_LINE_STRING_MARGIN_INDENT = 3;
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true;
  }

  public static Test suite() {
    return new MultiLineStringAlignInfixAllTest();
  }
}
