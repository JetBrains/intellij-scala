package org.jetbrains.plugins.scala.lang.actions.editor.enter;

import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.util.TestUtils;

/**
 * @author Alexander Podkhalyuzin
 */
public class EnterActionTest extends AbstractEnterActionTestBase {
  @NonNls
  private static final String DATA_PATH = "/actions/editor/enter/data";

  public EnterActionTest() {
    super(TestUtils.getTestDataPath() + DATA_PATH);
  }

  public static Test suite() {
    return new EnterActionTest();
  }
}
