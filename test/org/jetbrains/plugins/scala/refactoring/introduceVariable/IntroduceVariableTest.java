package org.jetbrains.plugins.scala.refactoring.introduceVariable;

import junit.framework.Test;
import org.jetbrains.annotations.NonNls;

/**
 * @author Alexander Podkhalyuzin
 */
public class IntroduceVariableTest extends AbstractIntroduceVariableTest {
  @NonNls
  private static final String DATA_PATH = "test/org/jetbrains/plugins/scala/refactoring/introduceVariable/data/";

  public IntroduceVariableTest() {
    super(DATA_PATH);
  }

  public static Test suite() {
    return new IntroduceVariableTest();
  }
}
