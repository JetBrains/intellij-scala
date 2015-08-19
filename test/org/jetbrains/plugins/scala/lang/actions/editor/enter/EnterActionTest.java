package org.jetbrains.plugins.scala.lang.actions.editor.enter;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.project.Project;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.util.TestUtils;
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
    super(TestUtils.getTestDataPath() + DATA_PATH);
  }

  @Override
  protected void setUp(Project project) {
    super.setUp(project);

    CodeInsightSettings.getInstance().JAVADOC_STUB_ON_ENTER = false; //No, we don't need it.
  }

  @Override
  protected void tearDown(Project project) {
    CodeInsightSettings.getInstance().JAVADOC_STUB_ON_ENTER = true;

    super.tearDown(project);
  }

  public static Test suite() {
    return new EnterActionTest();
  }
}
