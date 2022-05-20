package org.jetbrains.plugins.scala.lang.actions.editor.enter;

import com.intellij.openapi.project.Project;
import junit.framework.Test;
import org.jetbrains.annotations.NotNull;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

/**
 * User: Dmitry.Naydanov
 * Date: 09.07.14.
 */
@RunWith(AllTests.class)
public class AddUnitReturnTypeTest extends AbstractEnterActionTestBase {
  public AddUnitReturnTypeTest() {
    super("/actions/editor/enter/addunit");
  }

  @Override
  protected void setSettings(@NotNull Project project) {
    super.setSettings(project);

    getScalaSettings(project).ENFORCE_FUNCTIONAL_SYNTAX_FOR_UNIT = true;
  }

  public static Test suite() {
    return new AddUnitReturnTypeTest();
  }
}
