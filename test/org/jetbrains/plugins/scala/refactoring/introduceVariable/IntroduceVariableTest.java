package org.jetbrains.plugins.scala.refactoring.introduceVariable;

import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.util.TestUtils;

/**
 * @author Alexander Podkhalyuzin
 */
public class IntroduceVariableTest extends AbstractIntroduceVariableTestBase {
  @NonNls
  private static final String DATA_PATH = "/introduceVariable/data";

  public IntroduceVariableTest() {
    super(TestUtils.getTestDataPath() + DATA_PATH);
  }

  public static Test suite() {
    return new IntroduceVariableTest();
  }
}
