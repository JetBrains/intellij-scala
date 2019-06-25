package org.jetbrains.plugins.scala.lang.actions.editor.enter;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.project.Project;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

/**
 * @author Alexander Podkhalyuzin
 */
@RunWith(AllTests.class)
public class EnterActionTest extends AbstractEnterActionTestBase {
  @NonNls
  private static final String DATA_PATH = "/actions/editor/enter/data";

  public EnterActionTest() {
    super(DATA_PATH);
  }

  @Override
  public void setUp(Project project) {
    super.setUp(project);

    CodeInsightSettings.getInstance().JAVADOC_STUB_ON_ENTER = false; //No, we don't need it.
  }

  @Override
  protected void setSettings() {
    super.setSettings();
    getScalaSettings().USE_SCALADOC2_FORMATTING = true;
  }

  @Override
  public void tearDown(Project project) {
    CodeInsightSettings.getInstance().JAVADOC_STUB_ON_ENTER = true;

    super.tearDown(project);
  }

  public static Test suite() {
    return new EnterActionTest();
  }
}
