package org.jetbrains.plugins.scala.lang.actions.editor.enter;

import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import junit.framework.Test;
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings;

/**
 * User: Dmitry Naydanov
 * Date: 4/16/12
 */
public class MultiLineStringIndentTest extends AbstractEnterActionTestBase {
  private static final String DATA_PATH = "./test/org/jetbrains/plugins/scala/lang/actions/editor/enter/multiLineStringData/indentOnly";

  public MultiLineStringIndentTest() {
    super(DATA_PATH);
  }

  @Override
  protected void setUp() {
    super.setUp();
    final CommonCodeStyleSettings settings = getSettings();
    final ScalaCodeStyleSettings scalaSettings = settings.getRootSettings().getCustomSettings(ScalaCodeStyleSettings.class);

    scalaSettings.MULTILINE_STRING_SUPORT = ScalaCodeStyleSettings.MULTILINE_STRING_QUOTES_AND_INDENT;
    scalaSettings.MULTI_LINE_STRING_MARGIN_INDENT = 3;
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true;
  }

  public static Test suite() {
    return new MultiLineStringIndentTest();
  }
}
