package org.jetbrains.plugins.scala.lang.actions.editor.enter;

import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import junit.framework.Test;
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

/**
 * User: Dmitry.Naydanov
 * Date: 09.07.14.
 */
@RunWith(AllTests.class)
public class AddUnitReturnTypeTest extends AbstractEnterActionTestBase {
  public AddUnitReturnTypeTest() {
    super(TestUtils.getTestDataPath() + "/actions/editor/enter/addunit");
  }

  @Override
  protected void setUp(Project project) {
    super.setUp(project);

    final CommonCodeStyleSettings settings = getSettings();
    final ScalaCodeStyleSettings scalaSettings = settings.getRootSettings().getCustomSettings(ScalaCodeStyleSettings.class);
    scalaSettings.ENFORCE_FUNCTIONAL_SYNTAX_FOR_UNIT = true;
  }

  public static Test suite() {
    return new AddUnitReturnTypeTest();
  }
}
